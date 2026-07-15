# Своя иконка приложения

Положите сюда файл **`icon.png`** (квадратный, рекомендуется 512×512, PNG с прозрачностью).

Проект уже собирается с иконкой-заглушкой (тёмный фон + голубой глаз), так что можно
собирать APK и без этого шага. Но если хотите свою иконку:

## Вариант A — локально (Termux / Android Studio / любой ПК с Python)

```bash
pip install pillow
python3 tools/generate_icons.py icon/icon.png
```

Скрипт разложит иконку по всем нужным mipmap-папкам (`mipmap-mdpi` … `mipmap-xxxhdpi`)
и создаст круглую версию для `ic_launcher_round`.

## Вариант B — GitHub Actions делает это сам

Workflow (`.github/workflows/build.yml`) уже настроен: если в репозитории по пути
`icon/icon.png` лежит файл, сборка автоматически прогонит `tools/generate_icons.py`
перед компиляцией APK. Никаких доп. действий не нужно — просто закоммитьте свой
`icon/icon.png` через GitHub веб-интерфейс (или мобильный git-клиент) и запустите workflow.
