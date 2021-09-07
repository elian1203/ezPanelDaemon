## ezPanelDaemon

The daemon for ezPanel, the open source Minecraft server panel. Please check the
[ezPanelWeb](https://github.com/elian1203/ezPanelWeb) repository for all documentation

### Daemon Details

This Daemon runs on Java (jar) and can be set up as a service using systemctl for easy starting/stopping and auto start
if your linux server needs to reboot.

The daemon also requires you to set up the MySQL database and user prior to starting.
[(tutorial available on the ezPanelWeb Wiki)](https://github.com/elian1203/ezPanelWeb/wiki/MySQL-Set-Up)

See the below example service
file [(tutorial available on the ezPanelWeb Wiki)](https://github.com/elian1203/ezPanelWeb/wiki/Daemon-Installation)

```shell
[Unit]
Description=ezPanel Daemon Service
After=syslog.target network.target

[Service]
SuccessExitStatus=143

User=root
Group=root

Type=simple

WorkingDirectory=/opt/ezPanel
ExecStart=/bin/java -jar /opt/ezPanel/ezPanelDaemon.jar [mysql host] [created database] [created user] [password]
ExecStop=/bin/kill -15 $MAINPID

[Install]
WantedBy=multi-user.target
```