import os
from functools import lru_cache
from typing import Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sarvamai import SarvamAI

APP_NAME = "JARVIS Sarvam Backend"
MODEL = os.getenv("SARVAM_CHAT_MODEL", "sarvam-105b-conversations")

app = FastAPI(title=APP_NAME, version="0.1.0")


class Message(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)


class ChatRequest(BaseModel):
    messages: list[Message] = Field(min_length=1, max_length=20)


class ChatResponse(BaseModel):
    reply: str
    model: str


SYSTEM_PROMPT = """You are J.A.R.V.I.S., a helpful personal assistant.
Use English by default. If the user speaks or writes in Hindi or Punjabi,
reply naturally in that language. Be concise when speaking aloud.
Help with study topics step by step and provide fair-play Free Fire India
game guidance. Never claim to have performed a phone action; the Android
app handles approved local commands. Do not recommend cheats, hacks,
account theft, or unsafe actions. Address the user respectfully as Sir."""


@lru_cache(maxsize=1)
def get_client() -> SarvamAI:
    key = os.getenv("SARVAM_API_KEY")
    if not key:
        raise HTTPException(
            status_code=503,
            detail="Sarvam is not configured. Set SARVAM_API_KEY on the backend.",
        )
    return SarvamAI(api_subscription_key=key, timeout=30.0)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": APP_NAME,
        "sarvam_configured": bool(os.getenv("SARVAM_API_KEY")),
    }


@app.post("/v1/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    client = get_client()
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    messages.extend(
        {"role": item.role, "content": item.content}
        for item in request.messages
    )

    try:
        result = client.chat.completions(
            model=MODEL,
            messages=messages,
            max_tokens=500,
            temperature=0.3,
        )
        reply = (result.choices[0].message.content or "").strip()
        if not reply:
            raise HTTPException(status_code=502, detail="Sarvam returned an empty reply.")
        return ChatResponse(reply=reply, model=MODEL)
    except HTTPException:
        raise
    except Exception:
        # Do not expose provider responses, request headers, or credentials to clients.
        raise HTTPException(
            status_code=502,
            detail="Sarvam request failed. Check backend logs and Sarvam API status.",
        )
