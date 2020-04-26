#!/usr/bin/env bash

curl_installed=$(command -v curl)
audiowaveform_installed=$(command -v lib/audiowaveform)

if [[ -z $curl_installed ]]; then
  echo "Error: cURL is not installed."
  exit 1
fi

if [[ -z $audiowaveform_installed ]]; then
    os_type=$(echo $OSTYPE)
    if [[ os_type -eq "darwin" ]];then
      printf "* Downloading audiowaveform for macOS... "
      curl -Ls https://www.dropbox.com/s/ahqikw808knb5su/audiowaveform-darwin-1.4.1?raw=1 -o lib/audiowaveform
    elif [[ os_type -eq "linux-gnu" ]]; then
      printf "* Downloading audiowaveform for Linux... "
      curl -Ls https://www.dropbox.com/s/8ezc6k1fbg74n52/audiowaveform-linux-x64-1.4.1?raw=1 -o lib/audiowaveform
    else
      echo
      echo "Error: your OS $os_type is unsupported."
      exit 1
    fi
    if [[ $? -eq 0 ]]; then
        echo "OK"
      else
        echo "Error downloading"
      exit 1
      fi
      chmod +x lib/audiowaveform
else
    echo "* Local audiowaveform already installed"
fi
