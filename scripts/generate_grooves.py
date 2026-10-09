"""Create original, cyclic electro loops. Requires numpy; no sampled recordings."""
from pathlib import Path
import wave
import numpy as np

RATE = 22050
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'

def loop(index, bpm):
    beat = 60 / bpm
    n = round(beat * 32 * RATE)
    mix = np.zeros(n, dtype=np.float64)
    rng = np.random.default_rng(910 + index)
    def add(sound, start):
        positions = (round(start * RATE) + np.arange(len(sound))) % n
        np.add.at(mix, positions, sound)
    def tone(freq, duration, gain, kind='bass'):
        t = np.arange(round(duration * RATE))/RATE
        if kind == 'kick':
            phase = 2*np.pi*(48*t + 95*.028*(1-np.exp(-t/.028)))
            return np.sin(phase)*np.exp(-t/ .10)*gain
        if kind == 'noise':
            noise = rng.normal(0,1,len(t))
            high = noise - np.concatenate(([0],noise[:-1]))*.9
            return high*np.exp(-t/.026)*gain
        phase = 2*np.pi*freq*t
        envelope = np.minimum(1,t/.007)*np.exp(-t/(duration*.36))*np.minimum(1,(duration-t)/.025)
        return (np.sin(phase)+.28*np.sin(phase*2)+.12*np.sin(phase*3))*envelope*gain
    roots = ([73.416,65.406,58.27,65.406] if index < 3 else [[82.407,73.416,65.406,73.416],[65.406,82.407,97.999,73.416],[58.27,73.416,65.406,87.307]][index-3])
    for b in range(32):
        start=b*beat
        add(tone(0,.4,.7,'kick'),start)
        for subdivision in (0,.5): add(tone(0,.12,.065,'noise'),start+subdivision*beat)
        if b%4 in (1,3):
            add(tone(0,.22,.18,'noise'),start)
            add(tone(180,.13,.11),start)
            if index>0:
                for delay in (.008,.019,.031): add(tone(0,.1,.085,'noise'),start+delay)
        root=roots[b//8]
        for offset,mult in ((0,1),(.5,2 if b%2 else 1)):
            add(tone(root*mult,beat*.42,.27),start+offset*beat)
        if index in (2, 4, 5) or b%4==2:
            notes=[2,2.378414,2.996614,4]
            for s in range(4 if index in (2, 4, 5) else 2):
                add(tone(root*notes[(b+s)%4],beat*.35,.075),start+s*beat/4)
    mix -= mix.mean()
    fade = round(RATE * .005)
    mix[:fade] *= np.linspace(0, 1, fade)
    mix[-fade:] *= np.linspace(1, 0, fade)
    mix *= .82 / max(1,np.max(np.abs(mix)))
    pcm=(mix*32767).astype('<i2')
    with wave.open(str(ROOT/f'groove_{index}.wav'),'wb') as out:
        out.setnchannels(1);out.setsampwidth(2);out.setframerate(RATE);out.writeframes(pcm.tobytes())
    print(f'{bpm} BPM: {n/RATE:.2f}s, peak {np.max(np.abs(mix)):.3f}')

if __name__ == '__main__':
    ROOT.mkdir(parents=True,exist_ok=True)
    for i,bpm in enumerate((104,112,120,108,116,124)): loop(i,bpm)
