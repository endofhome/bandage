#!/usr/bin/env bash

curl_installed=$(command -v curl)
audiowaveform_installed=$(command -v lib/audiowaveform)
ffmpeg_installed=$(command -v lib/ffmpeg)
ffprobe_installed=$(command -v lib/ffprobe)

if [[ -z $curl_installed ]]; then
  echo "Error: cURL is not installed."
  exit 1
fi

if [[ -z $audiowaveform_installed ]]; then
    if [[ $OSTYPE == "darwin"* ]];then
      printf "* Downloading audiowaveform for macOS... "
      curl -Ls https://www.dropbox.com/s/ahqikw808knb5su/audiowaveform-darwin-1.4.1?raw=1 -o lib/audiowaveform
    elif [[ $OSTYPE == "linux-gnu" ]]; then
      printf "* Downloading audiowaveform for Linux... "
      curl -Ls https://www.dropbox.com/s/8ezc6k1fbg74n52/audiowaveform-linux-x64-1.4.1?raw=1 -o lib/audiowaveform
    else
      echo
      echo "Error: your OS $OSTYPE is unsupported."
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

if [[ -z $ffmpeg_installed ]]; then
    if [[ $OSTYPE == "darwin"* ]];then
      printf "* Downloading ffmpeg for macOS... "
      curl -Ls https://www.dropbox.com/s/zdby9dzm0r5jrhu/ffmpeg_darwin_4.2.2?raw=1 -o lib/ffmpeg
    elif [[ $OSTYPE == "linux-gnu" ]]; then
      printf "* Downloading ffmpeg for Linux... "
      curl -Ls https://www.dropbox.com/s/w1agd8evvbuz0d5/ffmpeg_linux_x64?raw=1 -o lib/ffmpeg
    else
      echo
      echo "Error: your OS $OSTYPE is unsupported."
      exit 1
    fi
    if [[ $? -eq 0 ]]; then
        echo "OK"
      else
        echo "Error downloading"
      exit 1
      fi
      chmod +x lib/ffmpeg
else
    echo "* Local ffmpeg already installed"
fi

if [[ -z $ffprobe_installed ]]; then
    if [[ $OSTYPE == "darwin"* ]];then
      printf "* Downloading ffprobe for macOS... "
      curl -Ls https://www.dropbox.com/s/ghla06caoahj5lu/ffprobe_darwin_4.2.2?raw=1 -o lib/ffprobe
    elif [[ $OSTYPE == "linux-gnu" ]]; then
      printf "* Downloading ffprobe for Linux... "
      curl -Ls https://www.dropbox.com/s/5fge9t5pmsf4gd4/ffprobe_linux_x64?raw=1 -o lib/ffprobe
    else
      echo
      echo "Error: your OS $OSTYPE is unsupported."
      exit 1
    fi
    if [[ $? -eq 0 ]]; then
        echo "OK"
      else
        echo "Error downloading"
      exit 1
      fi
      chmod +x lib/ffprobe
else
    echo "* Local ffprobe already installed"
fi
