Vagrant provider for jclouds
============================

Building
--------

1. Vagrant bindings
  * `git clone https://github.com/neykov/vagrant-java-bindings`
  * `cd vagrant-java-bindings`
  * `mvn clean install`
  * Copy `target/vagrant-java-bindings-0.0.1-SNAPSHOT.jar` to jcloud's classpath
2. Vagrant provider
  * `git clone https://github.com/neykov/jclouds-vagrant`
  * `cd jclouds-vagrant/vagrant`
  * `mvn clean install`
  * Copy `target/vagrant-2.0.0-SNAPSHOT.jar` to jcloud's classpath

Using in Apache Brooklyn
------------------------

Download a Vagrant box:
```
vagrant box add ubuntu/trustry64
```

In your `brooklyn.properties` configure the location and use it to deploy blueprints:
```
brooklyn.location.jclouds.vagrant.imageId=ubuntu/trusty64
brooklyn.location.jclouds.vagrant.identity=dummy
brooklyn.location.jclouds.vagrant.credential=dummy
```

`identity` and `credential` are required but not used.


Local caching proxy
-------------------

### Polipo

Use `polipo` for caching proxy. On OS X install with
```
sudo brew install polipo
```

From [SO](http://superuser.com/questions/192696/how-can-i-make-tor-and-polipo-run-and-automatically-restart-using-launchd-on-m):

* Create a config file at ~/.polipo/config

```
# logLevel = 0xFF
dnsNameServer=8.8.8.8
diskCacheRoot = "~/.polipo/cache/"

```

* As root create the file `/Library/LaunchDaemons/fr.jussieu.pps.polipo.plist`, replace $USER with your username:
```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Disabled</key>
    <false/>
        <key>Label</key>
        <string>fr.jussieu.pps.polipo</string>
        <key>ProgramArguments</key>
        <array>
                <string>/usr/local/bin/polipo</string>
                <string>-c</string>
                <string>/Users/$USER/.polipo/config</string>
        </array>
        <key>RunAtLoad</key>
        <true/>
    <key>OnDemand</key>
    <false/>
    <key>UserName</key>
    <string>$USER</string>
    <key>GroupName</key>
    <string>daemon</string>
    <key>StandardOutPath</key>
    <string>/Users/$USER/.polipo/polipo.log</string>
    <key>StandardErrorPath</key>
    <string>/Users/$USER/.polipo/polipo.log</string>
</dict>
</plist>
```

* `sudo chown root:wheel /Library/LaunchDaemons/fr.jussieu.pps.polipo.plist`
* `sudo chmod 755 /Library/LaunchDaemons/fr.jussieu.pps.polipo.plist`
* `sudo launchctl -w load /Library/LaunchDaemons/fr.jussieu.pps.polipo.plist`

### Vagrant

* `vagrant plugin install vagrant-proxyconf`
* add to `~/.vagrant.d/Vagrantfile`:
```
Vagrant.configure("2") do |config|
  if Vagrant.has_plugin?("vagrant-proxyconf")
    config.proxy.http     = "http://10.0.2.2:8123/"
    config.proxy.https    = "http://10.0.2.2:8123/"
    config.proxy.no_proxy = "localhost,127.0.0.1"
  end
end
```

Where `10.0.2.2` is the IP of your host as seen from the vagrant machines (in this case the NAT interface).


Limitations
-----------

* Machines are created sequentially, no support for parallel execution from virtualbox provider
* Something prevents using vagrant at the same time with other jclouds providers - they try to login with vagrant user.
