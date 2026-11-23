# /// script
# requires-python = ">=3.12,<3.13"
# dependencies = ["qwen-tts==0.1.1", "soundfile", "numpy", "mlx-whisper"]
# ///
"""Record the boxing coach in the voice of the rest-timer cues, locally, and check every take
with a speech recogniser: a take is kept only when what is heard is what was asked for.

Usage: uv run scripts/boxing-voice.py <reference.wav> <recordings.json> <out-dir>
The reference is `src/assets/voice/rest-over.mp3` as a wav. The JSON maps file names to spoken
texts: it is what `boxingRecordings()` in `src/domain/boxing.ts` returns. Encode the wavs to
src/assets/voice/timer/<name>.mp3 afterwards (mono, 44.1 kHz, 48 kbit/s) and check the mp3s once more.
"""
import json, re, sys
from pathlib import Path
import soundfile as sf, torch, mlx_whisper
from qwen_tts import Qwen3TTSModel

ONES = "zero one two three four five six seven eight nine ten".split()
FIX = {"job": "jab", "jap": "jab", "jabs": "jab", "gab": "jab", "jam": "jab", "jeb": "jab", "chab": "jab", "hooks": "hook",
       "to": "two", "too": "two", "for": "four", "won": "one", "sex": "six", "combo": "combos", "punches": "punch"}
def norm(text):
    text = text.lower().replace("-", " ").replace("upper cut", "uppercut")
    text = re.sub(r"\d", lambda m: " " + ONES[int(m[0])] + " ", text)
    words = re.sub(r"[^a-z ]", "", text).split()
    return [FIX.get(w, w) for w in words]

ref, todo, out = sys.argv[1], json.load(open(sys.argv[2])), Path(sys.argv[3])
out.mkdir(parents=True, exist_ok=True)
model = Qwen3TTSModel.from_pretrained("Qwen/Qwen3-TTS-12Hz-1.7B-Base", device_map="mps", dtype=torch.bfloat16, attn_implementation="sdpa")
voice = model.create_voice_clone_prompt(ref_audio=ref, ref_text="Time's up. Next set.")
for name, text in todo.items():
    target = out / f"{name}.wav"
    if target.exists():
        continue
    low, high = 0.25 + 0.03 * len(text), 1.0 + 0.16 * len(text)
    for seed in (7, 23, 42, 101, 202, 303, 404, 505, 606, 707):
        torch.manual_seed(seed)
        wavs, rate = model.generate_voice_clone(text=text, language="English", voice_clone_prompt=voice, max_new_tokens=160)
        seconds = len(wavs[0]) / rate
        tmp = out / f"{name}.try.wav"
        sf.write(tmp, wavs[0], rate)
        got = mlx_whisper.transcribe(str(tmp), path_or_hf_repo="mlx-community/whisper-large-v3-turbo", language="en")["text"]
        ok = norm(got) == norm(text) and low <= seconds <= high
        print(f"{name} seed {seed}: {seconds:.2f}s '{got.strip()}' {'OK' if ok else 'no'}", flush=True)
        if ok:
            tmp.rename(target)
            break
    else:
        print(f"{name}: FAILED", flush=True)
