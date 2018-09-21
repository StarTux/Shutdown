# Shutdown
*Trigger server shutdowns based on conditions, or manually*

## Description
This plugin allows for the configuration of several conditions to restart a server, which is low TPS (server lag), low free memory, lack of online players, or a maximum uptime.  Where it applies, the timespan within which the condition must be true, can be configured, as well as the duration of the shutdown progress.  For example, the plugin can be configured to trigger a shutdown when the server TPS is lower than 17 for more then 5 consecutive minutes, and will be announced 30 seconds ahead of time.  All publicly displayed messages can be configured as well.

## Commands
There is one admin command which requires the `shutdown.shutdown` permission.
- `/shutdown <seconds>` - Initiate shutdown.
- `/shutdown now` - Shutdown with a nice interval.
- `/shutdown info` - Print performance and timing information.
- `/shutdown reload` - Reload the configuration file.
- `/shutdown reset` - Reset all shutdown timings.
- `/shutdown cancel` - Cancel the currently ongoing shutdown.

## Permissions
- `shutdown.shutdown` - Use the `/shutdown` command.
- `shutdown.alert` - Receive an alwert when some shutdown conditions become true.
- `shutdown.notify` - Receive the standard broadcasts. Defaults to *true*.


