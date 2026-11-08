import os
from pathlib import Path

from sarvamai import SarvamAI


# Load the private .env file into the process environment without extra packages.
env_file = Path(__file__).with_name(".env")
if env_file.exists():
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            name, value = line.split("=", 1)
            os.environ.setdefault(name.strip(), value.strip().strip('"').strip("'"))

api_key = os.environ.get("SARVAM_API_KEY")
if not api_key:
    raise SystemExit("Missing SARVAM_API_KEY. Add your key to the .env file and run again.")

client = SarvamAI(api_subscription_key=api_key)
response = client.chat.completions(
    model="sarvam-105b-conversations",
    messages=[{"role": "user", "content": "Reply with exactly: Sarvam connection works."}],
    reasoning_effort=None,
    max_tokens=100,
)

print(response.choices[0].message.content)
