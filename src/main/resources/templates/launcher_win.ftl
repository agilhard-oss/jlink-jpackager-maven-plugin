@echo OFF
cd %~dp0
cd ..

<#if mainJar??>
bin/java ${jvmArgs!} -jar ${mainJar} $* ${arguments!} 
<#else>
<#if mainClass??>
<#if mainModule??>
bin/java ${jvmArgs!} -m ${mainModule}/${mainClass} $* ${arguments!} 
<#else>
bin/java ${jvmArgs!} ${mainClass} $* ${arguments!}
</#if>
<#else>
bin/java ${jvmArgs!} $* ${arguments!}
</#if>
</#if>
