# /// script
# requires-python = ">=3.12,<3.13"
# dependencies = ["qwen-tts==0.1.1", "soundfile", "numpy"]
# ///
"""Generate the spoken rest-timer cues with Qwen3-TTS VoiceDesign, locally.

Usage: uv run scripts/voice.py <out-dir>
Writes <out-dir>/<cue>-<seed>.wav, three takes per cue. Pick the takes by ear,
then encode them to src/assets/voice/<cue>.mp3 (see README).
"""

import sys
from pathlib import Path

import soundfile as sf
import torch
from qwen_tts import Qwen3TTSModel

VOICE = (
    "A woman in her thirties with a clear, warm American English voice. "
    "She speaks like a calm, confident fitness coach: steady pace, friendly, no shouting."
)
CUES = {
    "ten-seconds": "Ten seconds left.",
    "rest-over": "Time's up. Next set.",
}
SEEDS = [7, 23, 42]

out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)

model = Qwen3TTSModel.from_pretrained(
    "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign",
    device_map="mps",
    dtype=torch.bfloat16,
    attn_implementation="sdpa",
)

for name, text in CUES.items():
    for seed in SEEDS:
        torch.manual_seed(seed)
        wavs, rate = model.generate_voice_design(
            text=text, language="English", instruct=VOICE, max_new_tokens=120
        )
        sf.write(out / f"{name}-{seed}.wav", wavs[0], rate)
        print(f"{name}-{seed}: {len(wavs[0]) / rate:.2f}s", flush=True)
