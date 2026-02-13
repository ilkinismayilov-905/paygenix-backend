 # GITHUB COMMANDS

 ## Merge to Main :
git checkout main
git pull origin main
git add .
git commit --no-edit
git merge "your_branch"
git push origin main

 ## Fetch From Main(before coding):
git checkout main
git pull origin main
git checkout -b "your_new_branch"
# kod yaz
git add .
git commit -m "commit"
git push -u origin "your_new_branch"(sonraki zaman sadece "git push")

# Change mvn version
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

