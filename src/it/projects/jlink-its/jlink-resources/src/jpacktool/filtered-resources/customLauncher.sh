#!/bin/sh

j_res="$0"
while [ -h "$j_res" ] ; do
    cd "`dirname "$j_res"`"
    j_basename=`basename "$j_res"`
    j_res=`ls -l "$j_basename" | sed "s/.*$j_basename -> //g"`
done
cd "`dirname "$j_res"`"
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

