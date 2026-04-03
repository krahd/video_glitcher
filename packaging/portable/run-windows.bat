@echo off
setlocal

set "BASE_DIR=%~dp0"
set "GST_DIR=%BASE_DIR%video\windows-amd64"
set "CLASSPATH=%BASE_DIR%video_glitcher.jar;%BASE_DIR%lib\*"

java -cp "%CLASSPATH%" -Dgstreamer.library.path="%GST_DIR%" -Dgstreamer.plugin.path="%GST_DIR%\gstreamer-1.0" tom.videoGlitcher.VideoGlitcher
