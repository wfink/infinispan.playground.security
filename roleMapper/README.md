# infinispan.playground.security
Infinispan security example using LDAP for Infinispan server
============================================================

Author: Wolf-Dieter Fink
Technologies: Infinispan, Hot Rod


## What is it?

This example will show how the HotRod client and REST can access secured caches with the simple used and group properties files and role-mapper.

## Preparation

### Configure the cache
Add the following cache to the infinispan.xml configuration.
Note that the cache can be a clustered cache as well if you start several clustered instances.
```
  <cache-container name="default" statistics="true">
    <local-cache name="secured">
      <security>
        <authorization/>
      </security>
    </local-cache>
```
Without specific `roles` set for the `&lt;authorization/&gt;` element all global roles will apply for the cache.
Otherwise the roles can be limited to be more restictive for this cache.


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
As authorization is wanted it is required to align roles with permissions.

The identity mapper will match the user-name or the group-names directly to the role with the same name.
There is no possibility to change user permissions at runtime with the CLI command `user role`, it will cause an error message.

This is done by changing the following
~~~
      <security>
        <identity-role-mapper/>
        <authorization>
          <role name="appReader" permissions="READ"/>
          <role name="appWriter" permissions="READ WRITE"/>
          <role name="appAdmin" permissions="admin"/>
          <role name="appSuper" permissions="ALL"/>
        </authorization>
      </security>
   </cache-container>
~~~
See [Configure user roles and permissions](https://infinispan.org/docs/stable/titles/server/server.html#rbac-remote) for more details.

**Hint:** remove of &lt;authorization/&gt; from global and cache security ensure only all users are authenticated.

### Add a number of users with different permission
~~~
bin/cli.sh
[disconnected]> user create --groups=appAll --password=super super
[disconnected]> user create --groups=appRead --password=reader reader
[disconnected]> user create --groups="appRead,appWrite" --password=writer writer
[disconnected]> user create --groups=appAdmin --password=admin admin
~~~

## Run the example

With the current configuration there is no access possible as the mapper is missing and there are no users created.
If the different mappers should be tested this is a good point to save the `server` directory to have this as base for later.

The following commands or clients can be used to access the cache.

### Console

  The console access is restricted as long as the global cache-container and cache security authorization is enabled, removing it from cache and global will only authenticate the user and allow every access.
  Otherwise monitoring is disable for all members without Admin or Super roles as the MONITOR role is not applied.

### REST

      curl -v -u reader:reader -X PUT -d "test1" http://127.0.0.1:11222/rest/v2/caches/secured/1

Will fail because of missing the WRITE permission

      curl -v -u writer:writer -X PUT -d "test1" http://127.0.0.1:11222/rest/v2/caches/secured/1

Will add the test entry as it is member of appWriter group with WRITE permission

      curl -v -u <user>:<passwd> http://127.0.0.1:11222/rest/v2/caches/secured/1

returns the 'test1' if the user has READ permission


### HotRod Java client

After the server has been started run the following command:

      `mvn exec:java -Dexec.args="<user> <passwd>`

this client will try to read and write to the cache and show statistics, it will show whether the user has permissions for this or not.


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


### Enable the cluster-role-mapper

Add the cluster-role-mapper to the global security config.
~~~
  <cache-container name="default" statistics="true">
    ...
    <security>
      <authorization>
        <cluster-role-mapper/>
        <roles>

~~~
The mapper will work similar to identity-role-mapper if there are no changes to the existing users.
But add the possibility to change user roles at runtime with the CLI command `user roles`.

For this open the server/conf/groups.properties and delete all lines with user/group mapping.
If the client is now used there are no longer permissions for the users as there are no matching roles to grant permission.
Except the user 'admin' as this is a default mapping, the principal admin will have ALL permission!

Now it is possible to grant or deny roles to user with CLI
~~~
user roles grant --roles=appAll super
user roles grant --roles=appRead admin
user roles grant --roles=appRead,appWrite writer
user roles grant --roles=appRead reader
~~~

The user 'super' has now full access, but because we applied one role to 'admin' the default is overriden and only READ permission is applied.
All the roles can be removed with `user roles deny --roles=...`.
**Hint:** Role applied by user group settings can not be removed with the CLI command, it will only work for roles applied with CLI!


### Enable the cluster-permission-mapper

Change to cluster-permission-mapper.
~~~
  <cache-container name="default" statistics="true">
    ...
    <security>
      <authorization>
        <cluster-permission-mapper/>
        <roles>

~~~
The mapper will work similar to identity-role- or cluster-role-mapper if there are no changes to the existing users.
But add the possibility to change role permission at runtime with the CLI command `user roles`.

Even if the changes from the cluster-role example are applied or not this configuration will still work as before.

Now it is possible to  create new roles and grant or deny roles to user with CLI.
Add two caches App1 and App2
~~~
      <distributed-cache name="cache1">
        <security>
          <authorization roles="app1Read app1Write"/>
        </security>
      </distributed-cache>
      <distributed-cache name="cache2">
        <security>
          <authorization roles="app2Read app2Write"/>
        </security>
      </distributed-cache>
~~~
If the client is used to access the cache1 it will fail as there are no permissions.
~~~
mvn exec:java -Dexec.args="writer writer cache1"
~~~
Now we are able to add the necessary roles and permissions with CLI
~~~
user create --password=writer1 writer1
user create --password writer2 writer2

user roles create --permissions=READ,WRITE app1Write
user roles create --permissions=READ,WRITE app2Write
user roles grant --roles=app1Write writer1
user roles grant --roles=app2Write writer2
~~~
As result the user writer1 can access the cache1 and writer2 can access cache2, but not vice versa. As well the user writer has no access to the new cache#.
But as the cache 'secured' has no role limitation all writer users have READ WRITE permission for that cache.

**Hint:** Roles configured within infinispan.xml configuration can not be changed with the CLI commands!

With that setting it is possible to grant different permissions for different caches within the same DG server.
