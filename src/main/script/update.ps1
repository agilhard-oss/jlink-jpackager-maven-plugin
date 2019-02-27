
param (
    [Parameter(Mandatory=$true)][string]$server,
    [Parameter(Mandatory=$true)][string]$port,
    [switch]$install = $false,
    [switch]$wait = $false,
    [switch]$help = $false,
    [Parameter(ValueFromRemainingArguments)][System.Collections.Arraylist]$launcherArguments
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName PresentationFramework

[string]$app = "sample"
[string]$appname = "sample"
[string]$icon = "sample.ico"
[string]$mainClass = "myproject.Sample"

[string]$os = "windows"
[string]$Dest = $($HOME) + "\" + $($app) + "-runtime";
write-output "Dest=$($Dest)";

[string]$BaseUrl = "http://$($server):$($port)/$($app)/update/bootstrap/$($os)";
  
[string]$ScriptDir = Split-Path $script:MyInvocation.MyCommand.Path
 
function usage
{
    write-output ""
    write-output "usage:"
    write-output "update.ps1 -server <server> -port <port> [<additionalLauncherArguments>] [-install] [-wait] [-h]"
    Exit
}

function error_message
{
    param([string]$msg)
    [System.Windows.MessageBox]::Show($($msg), 'Error!', 'OK', 'ERROR')
}

function fail_and_exit
{
    param([string]$msg)
    write-output $($msg)
    error_message $($msg)
    Exit
}

if ( $help ) {
    usage
}

function Unzip
{
    param([string]$zipfile, [string]$outpath)
    $t = Test-Path $outpath;
    if ( $t ) {
        rm -r -fo $($outpath);
    }

    [System.IO.Compression.ZipFile]::ExtractToDirectory($zipfile, $outpath)
#   Start-Process -FilePath "$($ScriptDir)\unzip.exe" -ArgumentList "-q -d $($outpath) $($zipfile)" -WindowStyle Hidden -PassThru
}

function Update
{
       

write-output "BaseURL=$($BaseUrl)";


[string]$ScriptDir = Split-Path $script:MyInvocation.MyCommand.Path

[string]$url=$($BaseUrl)+"/"+"update.sha256";
[string]$output=$($ScriptDir)+"\"+"update.sha256";
 
$wc = New-Object System.Net.WebClient
write-output "downloading $($url) to $($output)";
 
 Try {
 $wc.DownloadFile($($url), $($output))
} Catch {
    fail_and_exit "Error downloading file $($url)";
}

$sha = Get-Content -Path $output

$mustUpdate = $true;

$Check="$($Dest)\update.sha256";

$e=Test-Path $Check;

if ( $e ) {
    write-output "Checking ${$Check$}"
    $sha2 = Get-Content -Path $Check
    if ( $($sha2) -eq $($sha) ) {
        $mustUpdate=$false;
    }
}


if ( $mustUpdate ) {

    write-output "Runtime Update required"

[string]$url=$($BaseUrl)+"/"+"update.zip";
[string]$output2=$($ScriptDir)+"\"+"update.zip";

write-output "downloading $($url) to $($output2)";
Try {
 $wc.DownloadFile($($url), $($output2))
} Catch {
    fail_and_exit "Error downloading file $($url)";
}

$hashFromFile = Get-FileHash $($output2)

if ( $hashFromFile.hash -ne $sha ) {

    rm -r -fo $($output)
    rm -r -fo $($output2)

    fail_and_exit "hash values differ\r\nexpected: $($sha)\r\n     got: $($hashFromFile.hash)";
} else {
    write-output "SHA Hash Values matches: $sha"; 
}

Unzip $($output2) $($Dest)

cp $($output) $($Check)

rm -r -fo $($output)
rm -r -fo $($output2)

} else {

    write-output "No Runtime Update required"

    rm -r -fo $($output)


}
}

function Install
{
[string]$ScriptName=$script:MyInvocation.MyCommand.Name
[string]$Starter="$($ScriptDir)\${ScriptName}";

[string]$Desktop=[Environment]::GetFolderPath("Desktop")

write-output "Starter=$($Starter)"
write-output "Desktop=$($Desktop)"
	
$Shell = New-Object -ComObject ("WScript.Shell")
[string]$lnkname="$($appname)"

$ShortCut = $Shell.CreateShortcut("$($Desktop)\$($lnkname).lnk")

$ShortCut.TargetPath="%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe"
$q=""
foreach ($arg in $launcherArguments ) {
	if ( $q -eq "" ) {
	   $q="`"$($arg)`""
	} else {
	   $q="$($q) `"$($arg)`""
	}
}

$ShortCut.Arguments="-WindowStyle Hidden -ExecutionPolicy Bypass -File $($ScriptDir)\$($ScriptName) `"-server`" `"$($server)`" `"-port`" `"$($port)`"  $($q)";

$ShortCut.WorkingDirectory = "$($Dest2)";
$ShortCut.WindowStyle = 1;
$ShortCut.IconLocation = "$($ScriptDir)\$($icon)";
$ShortCut.Description = "$($appname)";
$ShortCut.Save()
}


function Launch
{
    cd $($Dest)

    Start-Process -FilePath bin\java -ArgumentList "--module-path app\jar_auto -splash:conf\splash.gif $(mainClass) $($launcherArguments)" -WindowStyle Hidden -PassThru 
}


if ( $install ) {
Install
} else {
Update
Launch
}


if ( $wait ) {
$( Read-Host "Type anything to quit" );
}


