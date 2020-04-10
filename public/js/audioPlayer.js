const containerId = 'waveform';
const containerElement = document.getElementById(containerId);
const container = {
    id: containerId,
    src: containerElement.getAttribute('src'),
    peaks: JSON.parse(containerElement.getAttribute('peaks'))
};
const options = {
    container: '#' + container.id,
    backend: 'MediaElement',
    barWidth: 2,
    barHeight: 1,
    barGap: null,
    plugins: [
        WaveSurfer.cursor.create({}),
        WaveSurfer.regions.create({})
    ],
};
const wavesurfer = WaveSurfer.create(options);
const defaultRegionOptions = {
    loop: false,
    drag: true,
    resize: true,
    color: 'hsla(200, 50%, 70%, 0.4)'
};
wavesurfer.enableDragSelection(defaultRegionOptions);

function removeHtmlAudioPlayer() {
    const html5AudioPlayer = document.getElementById('audio-player');
    html5AudioPlayer.remove();
}

function showEnhancedAudioPlayer() {
    const wavePlayerAudioPlayer = document.getElementById('waveplayer');
    wavePlayerAudioPlayer.style.display = "block";
}

function initialiseWaveSurferAndPlayAudio() {
    wavesurfer.load(container.src, container.peaks);
    wavesurfer.play();
}

function addPlayPauseOnClickHandler() {
    const playPauseButton = document.getElementById('play-pause');
    playPauseButton.onclick = function() {
        wavesurfer.playPause();
        if (wavesurfer.isPlaying()) {
            playPauseButton.innerText = "❚❚"
        } else {
            playPauseButton.innerText = "►"
        }
    };
}

function addLoopButtonOnClickHandler() {
    document.getElementById('loop').onclick = function() {
        const selectedRegion = currentRegion();
        selectedRegion.loop = !selectedRegion.loop;
        toggleSwitchVisualStatus('loop', selectedRegion.loop)
    };
}

function addZoomSliderOnClickHandler() {
    document.getElementById('zoom').oninput = function() {
        wavesurfer.zoom(Number(this.value));
    };
}

function addVolumeSliderOnClickHandler() {
    document.getElementById('volume').oninput = function() {
        wavesurfer.setVolume(Number(this.value / 100));
    };
}

function addClearSelectedRegionsOnClickHandler() {
    document.getElementById('clear-selected-regions').onclick = function() {
        wavesurfer.clearRegions();
        toggleSwitchVisualStatus('loop', false)
    };
}

function addRegionOnClickHandler() {
    wavesurfer.on('region-created', function(newRegion) {
        Object.keys(wavesurfer.regions.list).forEach(regionId => {
            if (regionId !== newRegion.id) {
                wavesurfer.regions.list[regionId].remove();
            }
        })
    });
}

function toggleSwitchVisualStatus(switchElementId, switchOn) {
    const loopButton = document.getElementById(switchElementId);
    if (switchOn) {
        loopButton.style.color = 'red';
    } else {
        loopButton.style.color = 'black';
    }
}

function currentRegion() {
    const regions = Object.keys(wavesurfer.regions.list);
    if (regions.length > 1) {
        throw new Error("Too many regions present")
    } else {
        return wavesurfer.regions.list[regions[0]]
    }
}

function updateCurrentTime() {
    wavesurfer.on('audioprocess', function () {
        document.getElementById('current-time').innerText = convertSecondsToHMMSS(wavesurfer.getCurrentTime());
    });
}

function convertSecondsToHMMSS(rawSeconds) {
    const hours = Math.floor(rawSeconds % (3600 * 24) / 3600);
    const minutes = Math.floor(rawSeconds % 3600 / 60);
    const seconds = Math.floor(rawSeconds % 60);
    const hoursString = hours > 0 ? (hours + ":") : "";
    const minutesString = minutes.toString().padStart(2, '0') + ":";
    const secondsString = seconds.toString().padStart(2, '0');

    return hoursString + minutesString + secondsString;
}

removeHtmlAudioPlayer();
initialiseWaveSurferAndPlayAudio();
showEnhancedAudioPlayer();
addPlayPauseOnClickHandler();
addLoopButtonOnClickHandler();
addRegionOnClickHandler();
addClearSelectedRegionsOnClickHandler();
addZoomSliderOnClickHandler();
addVolumeSliderOnClickHandler();
updateCurrentTime();
