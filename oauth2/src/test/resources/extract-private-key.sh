#!/usr/bin/env bash

keytool -importkeystore \
  -srckeystore keystore.jks \
  -destkeystore keystore.p12 \
  -srcstoretype JKS \
  -deststoretype PKCS12 \
  -srcstorepass feign \
  -deststorepass OpenFeign

openssl pkcs12 -in keystore.p12 -nocerts -nodes -passin pass:OpenFeign -out private_key.pem

openssl pkcs12 -in keystore.p12 -clcerts -nokeys -passin pass:OpenFeign -out certificate.pem

openssl rsa -in private_key.pem -check