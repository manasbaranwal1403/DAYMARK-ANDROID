@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle was not found. Open this folder in Android Studio and choose Build ^> Build APK(s).
  echo See README.md for instructions.
  pause
  exit /b 1
)
gradle --no-daemon :app:assembleDebug
if errorlevel 1 exit /b %errorlevel%
echo.
echo APK created at app\build\outputs\apk\debug\app-debug.apk
pause
