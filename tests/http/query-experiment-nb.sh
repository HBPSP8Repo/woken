#!/bin/bash

http -v --verify=no -a admin:WoKeN --timeout 180 POST http://localhost:8087/mining/experiment \
         user:='{"code":"user1"}' \
         variables:='[{"code":"alzheimerbroadcategory"}]' \
         grouping:='[]' \
         covariables:='[{"code":"subjectage"}, {"code":"leftcuncuneus"}]' \
         targetTable='cde_features_a' \
         algorithms:='[{"code":"sgdNaiveBayes", "parameters": []}]' \
         validations:='[{"code":"kfold", "name": "kfold", "parameters": [{"code": "k", "value": "2"}]}]'