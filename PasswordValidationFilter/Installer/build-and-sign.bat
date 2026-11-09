del OS2faktorPasswordValidationFilter.exe
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" setup.iss
"C:\Program Files (x86)\Windows Kits\10\bin\10.0.22621.0\x64\signtool.exe" ^
  sign /td SHA256 /fd SHA256 ^
  /sha1 f58705666b404cfdec4f3534aaa689ef6f873cf6 ^
  /tr http://timestamp.globalsign.com/tsa/r6advanced1 ^
  "OS2faktorPasswordValidationFilter.exe"

pause
