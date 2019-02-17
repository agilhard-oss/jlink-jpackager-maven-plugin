@echo OFF
SET ScriptDir=%~dp0
SET ScriptDir=%ScriptDir:~0,-1%

cd %ScriptDir%
cd ..

<#if mainJar??>
start /B bin\java ${jvmArgs!} -jar ${mainJar} ${arguments!} %*
<#else>
<#if mainClass??>
<#if mainModule??>
start /B bin\java ${jvmArgs!} -m ${mainModule}/${mainClass} ${arguments!} %* 
<#else>
start /B bin\java ${jvmArgs!} ${mainClass} ${arguments!} %*
</#if>
<#else>
start /B bin\java ${jvmArgs!} ${arguments!} %*
</#if>
</#if>
