@echo OFF
cd %~dp0
cd ..

<#if mainJar??>
start bin\java ${jvmArgs!} -jar ${mainJar} $* ${arguments!} 
<#else>
<#if mainClass??>
<#if mainModule??>
start bin\java ${jvmArgs!} -m ${mainModule}/${mainClass} $* ${arguments!} 
<#else>
start bin\java ${jvmArgs!} ${mainClass} $* ${arguments!}
</#if>
<#else>
start bin\java ${jvmArgs!} $* ${arguments!}
</#if>
</#if>
