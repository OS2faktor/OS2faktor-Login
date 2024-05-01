# Installing OS2faktorLogin CredentialProvider

## Installing VC_Redist_x64

The file can be found under: `os2faktor-nsis/CredentialsProvider/CredentialProvider/Redist/VC_redist.x64.exe` 

Can be run silently with the command `.\VC_redist.x64.exe /q /norestart`



## Installing OS2faktor CredentialProvider

1. Build the .msi with the Advanced installer under `os2faktor-nsis/CredentialsProvider/AdvancedInstaller/os2faktor.aip`
2. Install it on windows with the following command `msiexec /i "Y:\os2faktor-nsis\CredentialsProvider\AdvancedInstaller\Setup Files\da\os2faktor-CredentialProvider.msi" /quiet`



## Editing the registry

Application settings can be found under `[HKEY_LOCAL_MACHINE\SOFTWARE\DigitalIdentity\OS2faktorLogin]`

### Setup for Dev or production

The following values must always be changed to match the setup being used:

1. clientApiKey - Matching the "api_key" in the windows_credential_provider_clients table
2. clientID - Matching the "name" in the windows_credential_provider_clients table
3. os2faktorUserDomain - Matching the domain of the users using the computer in the domains table
4. os2faktorBaseUrl - Matching the URL of the OS2faktorLogin IdP to use 

#### Logging

Logging is not enabled by default but if the following registry settings is present logging wil be put in the txt files specified

1. CredentialProviderLogPath - The main program log
2. CreateSessionLogPath - Used for establishing session with the browser extensions
3. ChangePasswordLogPath - Changes password via a popup on the Sign-in page
4. ResetPasswordLogPath - Changes password via the Ctrl + Alt + Delete key-combo in Windows

#### Customizing texts in the CredentialProvider

You can customise the text for the following things by adding the registry key with the text as a value.

| Registry key          | Default value if no registry key is set | Description                                                  |
| --------------------- | --------------------------------------- | ------------------------------------------------------------ |
| ResetPasswordLinkText | Jeg har glemt mit kodeord               | The text used for the reset password prompt on the login screen |



### Filtering away the default "Password Credential Provider"

To comepletely filter away the default password credentials provider, including the default "other user" signin option, leaving only the OS2faktor CredentialProvider just disable it in the list of registered credentialsproviders like so:

```
[HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\{60b78e88-ead8-445c-9cfd-0b87f74ea6cd}]
"Disabled"=dword:00000001
```



### Selecting the default CredentialProvider

Selecting the default CredentialProvider means that new users will have the os2faktor WCP selected by default. This also, *and most importantly*, applies to the "Other User/Anden Bruger" Tile on the login screen. 

This is done by setting a Group Policy:

In the Group policy editor navigate to the following folder: `Computer Configuration -> Policies -> Administrative Templates -> System -> Logon` 

Enable the policy `Assign a default credential provider` and in the lower left set the option to `{0e4029c5-fab7-44dc-ac19-7060791f9b19}`
