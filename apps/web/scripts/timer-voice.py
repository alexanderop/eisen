# /// script
# requires-python = ">=3.12,<3.13"
# dependencies = ["qwen-tts==0.1.1", "soundfile", "numpy"]
# ///
"""Generate the spoken interval-timer cues in the voice of the rest-timer cues, locally.

Usage: uv run scripts/timer-voice.py <reference.wav> <out-dir>
The reference is `src/assets/voice/rest-over.mp3` as a wav: Qwen3-TTS clones its voice, so every
cue sounds like the same coach. Writes <out-dir>/<cue>.wav; encode them to
src/assets/voice/timer/<cue>.mp3 afterwards (mono, 44.1 kHz, 48 kbit/s).
A take whose length is implausible for its text is generated again with another seed.
"""

import sys
from pathlib import Path

import soundfile as sf
import torch
from qwen_tts import Qwen3TTSModel

REFERENCE_TEXT = "Time's up. Next set."
ONES = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split()
TENS = {20: "twenty", 30: "thirty", 40: "forty", 50: "fifty", 60: "sixty"}


def words(n: int) -> str:
    if n < 20:
        return ONES[n]
    tens, ones = n - n % 10, n % 10
    return TENS[tens] if ones == 0 else f"{TENS[tens]}-{ONES[ones]}"


CUES = {
    "get-ready": "Get ready.",
    "go": "Go!",
    "rest": "Rest.",
    "halfway": "Halfway.",
    "last-round": "Last round.",
    "thirty-seconds": "Thirty seconds.",
    "ten-seconds": "Ten seconds.",
    "complete": "Workout complete.",
}
for n in range(1, 61):
    spoken = words(n).capitalize()
    CUES[f"round-{n}"] = f"Round {words(n)}."
    CUES[f"minutes-left-{n}"] = f"{spoken} minute{'' if n == 1 else 's'} left."
    CUES[f"minutes-{n}"] = f"{spoken} minute{'' if n == 1 else 's'}."

reference, out = sys.argv[1], Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)

model = Qwen3TTSModel.from_pretrained(
    "Qwen/Qwen3-TTS-12Hz-1.7B-Base",
    device_map="mps",
    dtype=torch.bfloat16,
    attn_implementation="sdpa",
)
voice = model.create_voice_clone_prompt(ref_audio=reference, ref_text=REFERENCE_TEXT)

for name, text in CUES.items():
    target = out / f"{name}.wav"
    if target.exists():
        continue
    # About 0.09s a letter at a coach's pace. A take far off that is a slip of the model.
    low, high = 0.25 + 0.03 * len(text), 0.9 + 0.14 * len(text)
    for seed in (7, 23, 42, 101, 202, 303):
        torch.manual_seed(seed)
        wavs, rate = model.generate_voice_clone(
            text=text, language="English", voice_clone_prompt=voice, max_new_tokens=120
        )
        seconds = len(wavs[0]) / rate
        if low <= seconds <= high:
            sf.write(target, wavs[0], rate)
            print(f"{name}: {seconds:.2f}s (seed {seed})", flush=True)
            break
        print(f"{name}: {seconds:.2f}s out of range, again", flush=True)
    else:
        print(f"{name}: FAILED", flush=True)
