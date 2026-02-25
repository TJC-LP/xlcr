#!/usr/bin/env python3
"""Generate a minimal EML file for native-image tracing agent tests."""
import sys
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formatdate

output = sys.argv[1] if len(sys.argv) > 1 else "test.eml"

msg = MIMEMultipart()
msg["From"] = "sender@example.com"
msg["To"] = "recipient@example.com"
msg["Subject"] = "XLCR Test Email"
msg["Date"] = formatdate(localtime=True)

# Text body
msg.attach(MIMEText(
    "This is a minimal test email used by the GraalVM tracing agent "
    "to capture Jakarta Mail reflection metadata for native image builds.",
    "plain"
))

# Small text attachment
attachment = MIMEText("Attachment content for testing.", "plain")
attachment.add_header("Content-Disposition", "attachment", filename="test-attachment.txt")
msg.attach(attachment)

with open(output, "w") as f:
    f.write(msg.as_string())
print(f"Generated {output}")
