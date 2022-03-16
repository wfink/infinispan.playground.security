# infinispan.playground.security
Infinispan security example using LDAP for Infinispan server
============================================================

Author: Wolf-Dieter Fink
Technologies: Infinispan, Hot Rod, LDAP


## What is it?

This example will show how the HotRod client and REST can access LDAP secured caches.

The example is based on the [open LDAP docker test available at github](https://github.com/rroemhild/docker-test-openldap) by Rafael Roemhild.
The Infinispan server will use the provided LDAP data to authenticate and authorize the access to caches by REST and Java HotRod client. As well the console access is managed.


## Preparation

### Install and run the docker image
Follow the README from [OpenLDAP docker image](https://github.com/rroemhild/docker-test-openldap) the root project folder to prepare a server instance.
Or simply use the following commands, note you need to have docker or podman installed!
If you use only docker, simply replace the `podman` with `docker`.
```
podman pull rroemhild/test-openldap
podman run --rm -p 10389:10389 -p 10636:10636 rroemhild/test-openldap
```


### Configure the LDAP security-realm
LDAP configuration is [documented here](https://infinispan.org/docs/stable/titles/server/server.html#ldap-security-realms_security-realms)
```
         <security-realms>
            <security-realm name="futurama">
              <!-- Names an LDAP realm and specifies connection properties. -->
              <ldap-realm name="ldap"
                    url="ldap://localhost:10389"
                    principal="cn=admin,dc=planetexpress,dc=com"
                    credential="GoodNewsEveryone"
                    direct-verification="true"
                    >
                <!-- Defines how principals are mapped to LDAP entries. -->
                <identity-mapping rdn-identifier="uid" search-dn="ou=people,dc=planetexpress,dc=com">
                   <!-- Retrieves all the groups of which the user is a member. -->
                   <attribute-mapping>
                      <attribute from="cn"
                             to="Roles"
                             filter="(&amp;(objectClass=Group)(member={1}))"
                             filter-dn="ou=People,dc=planetexpress,dc=com"/>
                   </attribute-mapping>
                  <user-password-mapper from="userPassword" verifiable="false"/>
                </identity-mapping>
              </ldap-realm>
            </security-realm>
```

The identity mapping will find the given user by searching the `dn` with the username as `uid`.
The attribute-mapping will extract the role from `cn` attribute where the given user is a member of the objectClass 'Group'.

### Configure the endpoints
```
         <endpoint socket-binding="default" security-realm="futurama">
            <hotrod-connector name="hotrod">
                    <authentication>
                            <sasl mechanisms="PLAIN"/>
                    </authentication>
            </hotrod-connector>
            <rest-connector name="rest">
                    <authentication mechanisms="BASIC"/>
            </rest-connector>
         </endpoint>
```

The endpoint for HotRod must use the SASL mechanism `PLAIN` and for REST the authentication mechanism `BASIC` must be set, there is no other possibilty.
A more secure alternative is to use Kerberos which allows Negotiate and GSSAPI mechanisms.

### Configure global security
If authorization is wanted it is required to align the groups read as roles from LDAP with permissions.
This is done by changing the following
~~~
      <security>
        <authorization>
          <role name="ship_crew" permissions="READ"/>
          <role name="admin_staff" permissions="ALL"/>
        </authorization>
      </security>
   </cache-container>
~~~
The `ship_crew` will be able to read entries and the `admin_staff` has permissions for all.
See [Customizing roles and permissions](https://infinispan.org/docs/stable/titles/server/server.html#customizing-authorization_security-authorization) for more details.

**Hint:** remove of &lt;authorization/&gt; from global and cache security ensure only all users are authenticated.

### Configure the cache
Add the following cache to the infinispan.xml configuration, the <authorization/> will inherit the global settings for the cache.
Note that the cache can be a clustered cache as well if you start several clustered instances.
```
   <cache-container name="default" statistics="true">
      <local-cache name="futurama">
              <security>
                      <authorization/>
              </security>
      </local-cache>
```
It is also possible to use a mapping different from the global one to be more or less restictive for this cache.

## Run the example

### Console

  The console access is restricted as long as the global cache-container and cache security authorization is enabled, removing it from cache and global will only authenticate the user and allow every access.
  Otherwise monitoring is disable for all members of 'ship_crew' as the MONITOR role is not applied, members of 'admin_staff' can monitor because of ALL permissions.

### REST

      curl -v -u fry:fry -X PUT -d "test1" http://127.0.0.1:11222/rest/v2/caches/futurama/1

Will fail because of missing the WRITE permission

      curl -v -u professor:professor -X PUT -d "test1" http://127.0.0.1:11222/rest/v2/caches/futurama/1

Will add the test entry as it is member of admin_staff with WRITE permission

      curl -v -u fry:fry http://127.0.0.1:11222/rest/v2/caches/futurama/1

returns the 'test1' as the user has READ permission


### HotRod Java client

After the server has been started run the following command:

      `mvn clean test`

to start a simple test whether the permissions are as expected.


### Enable logging for security
To track what is going on it is possible to use the `org.wildfly.security` category with `DEBUG` or `TRACE` level, as well the LDAP Image will show the requests.
Add the category as followed and maybe change the console appender to show the logging on the console for the test.
~~~
  <Loggers>
    <Root level="INFO">
      <AppenderRef ref="STDOUT" level="TRACE"/>   <<--

      ...
    </Root>
...
    <Logger name="org.wildfly.security" level="TRACE"/>
~~~
