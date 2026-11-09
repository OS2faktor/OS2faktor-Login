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
    $arr = [string[]]@()
}

if (-not $arr) { $arr = @() }
if ($arr -notcontains $Package) {
    $arr += $Package 
    $key.SetValue($value, [string[]]$arr, [Microsoft.Win32.RegistryValueKind]::MultiString)
}

$key.Close()
$reg.Close()

exit 0
