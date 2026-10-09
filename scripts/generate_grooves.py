"""Create original, cyclic electro loops. Requires numpy; no sampled recordings."""
from pathlib import Path
import wave
import numpy as np

RATE = 22050
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'

def midnight_signal():
    """Original minor-key analogue sequence with a soft heartbeat and long synth bed."""
    bpm = 96
    beat = 60 / bpm
    n = round(beat * 32 * RATE)
    music = np.zeros(n)
    rhythm = np.zeros(n)
    def add(sound, start, target=music):
        np.add.at(target, (round(start * RATE) + np.arange(len(sound))) % n, sound)
    def synth(midi, duration, gain, pad=False):
        t = np.arange(round(duration * RATE)) / RATE
        f = 440 * 2 ** ((midi - 69) / 12)
        phase = 2 * np.pi * f * t
        if pad:
            signal = .5*np.sin(phase*.9987) + .5*np.sin(phase*1.0013) + .13*np.sin(phase*2)
            env = np.minimum(1,t/.22) * np.minimum(1,(duration-t)/.35)
        else:
            # Smooth harmonic roll-off avoids bright percussion-like transients.
            signal = sum(np.sin(phase*k)*np.exp(-k*(.25+t*3))/k for k in range(1,9))
            env = np.minimum(1,t/.012)*np.minimum(1,(duration-t)/.09)*(.16+.84*np.exp(-t/.19))
        return signal * env * gain
    roots = (50,46,43,45)
    steps = (0,7,14,10,3,12,7,17,14,3,10,7,12,5,17,10)
    for section,root in enumerate(roots):
        start = section * 8 * beat
        chord = (0,3,7,14) if section != 3 else (0,5,7,10)
        for interval in chord:
            add(synth(root+interval,beat*8.6,.065,True),start)
        for tick in range(32):
            when = start + tick * beat / 4
            tone = synth(root+12+steps[(tick+section*3)%16],beat*.62,.21)
            add(tone,when)
            add(tone*.18,when+beat*.75)
            add(tone*.075,when+beat*1.5)
        for b in range(8):
            add(synth(root-12,beat*1.2,.19),start+b*beat)
            t = np.arange(round(RATE*.24))/RATE
            pulse = np.sin(2*np.pi*(43*t+26*.035*(1-np.exp(-t/.035)))) * np.exp(-t/.09)*.075
            add(pulse,start+b*beat,rhythm)
            if b % 2 == 0:add(pulse*.3,start+(b+.625)*beat,rhythm)
    rms = np.sqrt(np.mean(music**2))
    drum_rms = np.sqrt(np.mean(rhythm**2))
    if drum_rms > rms*.3:rhythm *= rms*.3/drum_rms
    mix = music + rhythm
    mix -= mix.mean()
    fade = round(RATE*.005)
    mix[:fade] *= np.linspace(0,1,fade)
    mix[-fade:] *= np.linspace(1,0,fade)
    mix *= .78/np.max(np.abs(mix))
    with wave.open(str(ROOT/'groove_0.wav'),'wb') as out:
        out.setnchannels(1);out.setsampwidth(2);out.setframerate(RATE)
        out.writeframes((mix*32767).astype('<i2').tobytes())
    print(f'0: Midnight Signal, {bpm} BPM, {n/RATE:.2f}s, original notes and synthesis')

def loop(index, bpm):
    beat = 60 / bpm
    n = round(beat * 32 * RATE)
    mix = np.zeros(n, dtype=np.float64)
    drums = np.zeros(n, dtype=np.float64)
    rng = np.random.default_rng(910 + index)
    def add(sound, start, rhythm=False):
        positions = (round(start * RATE) + np.arange(len(sound))) % n
        np.add.at(drums if rhythm else mix, positions, sound)
    def note(midi, duration, gain, voice='bass'):
        t = np.arange(round(duration * RATE))/RATE
        frequency = 440 * 2**((midi-69)/12)
        phase = 2*np.pi*frequency*t
        attack = .13 if voice == 'pad' else .008
        release = .2 if voice == 'pad' else .035
        envelope = np.minimum(1,t/attack)*np.minimum(1,(duration-t)/release)
        if voice == 'pad':
            signal = (.50*np.sin(phase*.998)+.50*np.sin(phase*1.002)+.14*np.sin(phase*2))*(.92+.08*np.sin(2*np.pi*.6*t))
        elif voice == 'keys':
            signal = np.sin(phase+1.6*np.sin(phase*2)*np.exp(-t/.3))*np.exp(-t/.55)
        elif voice == 'arcade':
            signal = sum(np.sin(phase*k)/k for k in (1,3,5,7))/1.5*np.exp(-t/.22)
        elif voice == 'lead':
            signal = np.sin(phase+ .025*np.sin(2*np.pi*5*t))+.18*np.sin(phase*2)
        else:
            signal = (np.sin(phase)+.18*np.sin(phase*2))*np.exp(-t/(duration*.7))
        return signal*envelope*gain
    def kick():
        t = np.arange(round(RATE*.3))/RATE
        return np.sin(2*np.pi*(46*t+65*.025*(1-np.exp(-t/.025))))*np.exp(-t/.065)*.20
    def noise(duration,gain):
        t = np.arange(round(RATE*duration))/RATE
        raw = rng.normal(0,1,len(t))
        high = raw-np.concatenate(([0],raw[:-1]))*.9
        return high*np.exp(-t/.022)*gain
    roots = ([57,53,48,55],[52,55,57,59],[50,46,53,48],[48,51,46,53],[57,60,53,55],[50,53,48,57])[index]
    style = index % 3
    intervals = (0,3,7) if style!=1 else (0,4,7,10)
    for bar in range(8):
        root = roots[(bar//2)%4]
        for interval in intervals:
            add(note(root+12+interval,beat*4.25,.105 if style==0 else .055,'pad'),bar*4*beat)
        if style==1:
            for hit in (0,1.75,3):
                for interval in intervals:add(note(root+24+interval,beat*.9,.11,'keys'),(bar*4+hit)*beat)
        if style==0:
            melody = (7,10,12,7,3,5,7,2)
            for hit in (1,2.5):add(note(root+24+melody[(bar+int(hit))%8],beat*.9,.14,'lead'),(bar*4+hit)*beat)
        if style==2:
            melody=(0,7,12,15,12,7,3,10)
            for hit in range(8):add(note(root+24+melody[(hit+bar)%8],beat*.36,.18,'arcade'),(bar*4+hit*.5)*beat)
        for b in range(4):
            start=(bar*4+b)*beat
            if style==0 or style==1 and b in (0,2) or style==2 and b%2==0:add(kick(),start,True)
            if b in (1,3):add(noise(.16,.060),start,True)
            for hat in ((.5,) if style==0 else (0,.5)):add(noise(.07,.017),start+hat*beat,True)
            bass_hits=(0,.75) if style==1 else (0,)
            for offset in bass_hits:add(note(root-12+(12 if style==1 and offset else 0),beat*.55,.14),start+offset*beat)
    # Keep the musical voices ahead of percussion in every arrangement.
    rhythm_rms = np.sqrt(np.mean(drums**2))
    music_rms = np.sqrt(np.mean(mix**2))
    if rhythm_rms > music_rms*.5:drums *= music_rms*.5/rhythm_rms
    mix += drums
    mix -= mix.mean()
    fade = round(RATE * .005)
    mix[:fade] *= np.linspace(0, 1, fade)
    mix[-fade:] *= np.linspace(1, 0, fade)
    mix *= .78 / max(.01,np.max(np.abs(mix)))
    pcm=(mix*32767).astype('<i2')
    with wave.open(str(ROOT/f'groove_{index}.wav'),'wb') as out:
        out.setnchannels(1);out.setsampwidth(2);out.setframerate(RATE);out.writeframes(pcm.tobytes())
    print(f'{index}: {bpm} BPM, {n/RATE:.2f}s, percussion/music RMS <= 0.5, peak {np.max(np.abs(mix)):.3f}')

if __name__ == '__main__':
    ROOT.mkdir(parents=True,exist_ok=True)
    midnight_signal()
    for i,bpm in enumerate((104,112,120,108,116,124)):
        if i:loop(i,bpm)
