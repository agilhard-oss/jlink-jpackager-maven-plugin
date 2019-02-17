[string]$ScriptDir = Split-Path $script:MyInvocation.MyCommand.Path
cd "$($ScriptDir)\.."


<#if mainJar??>
Start-Process -FilePath bin\java -ArgumentList "${jvmArgs!} -jar ${mainJar} %* ${arguments!} $args" -NoNewWindow -PassThru
<#else>
<#if mainClass??>
<#if mainModule??>
Start-Process -FilePath bin\java -ArgumentList "${jvmArgs!} -m ${mainModule}/${mainClass} ${arguments!} $args" -NoNewWindow -PassThru
<#else>
Start-Process -FilePath bin\java -ArgumentList "${jvmArgs!} ${mainClass} ${arguments!} $args" -NoNewWindow -PassThru
</#if>
<#else>
Start-Process -FilePath bin\java -ArgumentList "${jvmArgs!} ${arguments!} $args" -NoNewWindow -PassThru
</#if>
</#if>
