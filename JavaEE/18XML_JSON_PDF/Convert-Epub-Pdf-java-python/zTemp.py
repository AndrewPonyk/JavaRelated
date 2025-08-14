
from openai import OpenAI

BASE_URL = "https://openrouter.ai/api/v1"
import os
api_key = "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"
if not api_key:
    raise RuntimeError("Please set the OPENROUTER_API_KEY environment variable.")
client = OpenAI(api_key=api_key, base_url=BASE_URL)

response = client.chat.completions.create(
  model="deepseek/deepseek-chat-v3-0324:free",
  messages=[
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Say hello!"}
  ]
)

reply = response.choices[0].message.content
print("DeepSeek: " + reply)
