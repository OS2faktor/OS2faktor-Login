function Invoke-Method {
	param(
        [string] $SAMAccountName = $(throw "Please specify a sAMAccountName.")
	)
	
	$result = "Password for " + $SAMAccountName + " expires soon"

	$result | Out-File 'c:\logs\log.txt'
}