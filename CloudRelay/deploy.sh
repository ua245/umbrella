#!/bin/bash

# Ensure you have authenticated with gcloud before running this:
# gcloud auth login
# gcloud config set project YOUR_PROJECT_ID

gcloud run deploy umbrella-cloud-relay \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --port 8080
