#!/bin/bash
cp src/manifest.json backup
cp manifest.json src
(cd src; zip -r ../app.zip *)
mv backup src/manifest.json
