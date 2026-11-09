param(
    [Parameter(Mandatory=$true)]
    [string]$Package
)

$subkey = 'SYSTEM\CurrentControlSet\Control\LSA'
$value  = 'Notification Packages'

$reg = [Microsoft.Win32.RegistryKey]::OpenBaseKey([Microsoft.Win32.RegistryHive]::LocalMachine, "Default")
$key = $reg.OpenSubKey($subkey, $true)

$arr = $key.GetValue($value)
if ($null -eq $arr) {
    $key.Close()
    $reg.Close()
    exit 0
}

if ($arr -contains $Package) {
    $arr = $arr | Where-Object { $_ -ne $Package }

    $key.SetValue($value, [string[]]$arr, [Microsoft.Win32.RegistryValueKind]::MultiString)
}

$key.Close()
$reg.Close()

exit 0
