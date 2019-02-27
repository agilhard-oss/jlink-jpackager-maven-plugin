#!/bin/bash

app="sample"
icon="sample.ico"
mainClass="myproject.Sample"

os=`uname`
if [ "${os}" == "Darwin" ]; then
os="mac"
elif [ "${os} == "Linux ]; then
os="linux"
fi

usage() {
    echo
    echo "usage:"
    echo "update.sh -server <server> -port <port> [<additionalLauncherArguments>] [-install] [-wait] [-h]"
    exit 0;
}

zenity_msg () {
	which zenity > /dev/null 2>&1
	if [ $? -eq 0 ]; then
		zenity --$1 --text "$2"
	fi
}

fail_and_exit () {
	# args $1 failure message
	echo "$1"
	zenity_msg "error" "$1"
	exit 1
}

wait="false"
install="false"

function quote() {
   arr=("$@")
   q=""
   for i in "${arr[@]}";
      do
          if [ "${q}" == "" ]; then
              q="\"$i\""
	  else
              q="${q} \"$i\""
	  fi
     done
   echo $q
}

declare -a launcherArguments

until [ -z "$1" ]; do
  case $1 in
      "-h")
	  shift
	  usage
      ;;
      "-server")
	  shift
	  if [ $# -eq 0 ]; then
	      usage
	  fi
	  server=$1
	  shift
      ;;
      "-port")
	  shift
	  if [ $# -eq 0 ]; then
	      usage
	  fi
	  port=$1
	  shift
      ;;
      "-install")
	  shift
	  install="true"
      ;;
      "-wait")
	  shift
	  wait="true"
      ;;
      *)
	  launcherArguments+=$1;
	  shift
  esac
done    

if [ "$server" == "" ]; then
    echo -n "server: "
    read server
fi
if [ "$port" == "" ]; then
    echo -n "port: "
    read port
fi


Dest="${HOME}/${app}-runtime"

ScriptDir=$(readlink -f "$0")
ScriptName=$(basename "${ScriptDir}")
ScriptDir=$(dirname "${ScriptDir}") 

Update() {
BaseUrl="http://${server}:${port}/${app}/update/bootstrap/${os}"

echo "BaseUrl=${BaseUrl}"

cd "${ScriptDir}"

output="${ScriptDir}/update.sha256"

url="${BaseUrl}/update.sha256"
curl -f -s $url -o "${output}"
if [ $? -ne 0 ]; then
    fail_and_exit "error downloading ${url}"
fi

sha=`cat update.sha256`
mustUpdate="true" 

Check="${Dest}/update.sha256"

if [ -f "${Check}" ]; then
    sha2=`cat "${Check}"`
    if [ "${sha}" == "${sha2}" ]; then
	mustUpdate="false"
    else
	echo "hash values differ";
	echo "expected: $sha";
	echo "     got: $sha2";
    fi
else 
    echo "checksum file not present ${Check}"
fi

if [ ${mustUpdate} == "true" ]; then
    echo "Runtime Update required"
    url="${BaseUrl}/update.zip"
    output2="${ScriptDir}/update.zip"
    echo "downloading ${url}"
    curl -f -s $url -o "${output2}"
     if [ $? -ne 0 ]; then
	 fail_and_exit "error downloading ${url}"
     fi
     sha2=`sha256sum "${output2}" | awk '{print $1}'`
    if [ "${sha}" != "${sha2}" ]; then
	rm -f ${output2}
	rm -f ${output}
	fail_and_exit "hash values differ\nexpected: $sha\n     got: $sha2";
    else
	echo "SHA Hash Values matches: $sha"; 
    fi

    rm -rf "${Dest}"
    unzip -q "${output2}" -d "${Dest}"

    if [ $? -ne 0 ]; then
	rm -f ${output2}
	rm -f ${output}
	fail_and_exit "error unzipping ${output2}"
    fi

    cp "${output}" "${Check}"

    rm -f ${output2}
    rm -f ${output}

else
    echo "No Runtime Update required"
    rm -f ${output}
fi


}

Launch() {
    cd $Dest
    bin/java --module-path app/jar_auto -splash:conf/splash.gif ${mainClass} ${launcherArguments}
}

Install() {
    if [ "${os}" == "mac" ]; then
         fail_and_exit "Install not implemented for Mac"
    fi
    q=$(quote ${launcherArguments})

    cat <<EOF >"${HOME}/Desktop/${app}.desktop"
[Desktop Entry]
    Exec="${ScriptDir}/${ScriptName}" -server "$server" -port "${port}" ${q}
    Encoding=UTF-8
    Icon=${ScriptDir}/${icon}
    Name=${app}
    Path=${ScriptDir}
    Type=Application
EOF

}

if [ "${install}" == "true" ]; then
    Install
else
    Update
    Launch
fi

if [ "${wait}" == "true" ]; then
    echo -n "Type return to quit! "
    read a
fi
