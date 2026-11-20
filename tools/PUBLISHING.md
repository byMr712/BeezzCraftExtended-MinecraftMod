# Публикация BeezzCraft (Extended) на CurseForge

Проект: **1817202** — <https://authors.curseforge.com/#/projects/1817202/files>

Скрипты загружают файлы через API консоли автора, используя cookie-сессию
Chrome. API-ключ не нужен.

---

## 0. Что понадобится

| Что | Зачем |
|---|---|
| Chrome | сессия автора CurseForge |
| Python 3.10+ (`py`) | скрипты |
| `pip install selenium` | управление Chrome |
| JDK 25 | сборка (Loom 1.18.4 требует JVM 25) |

Проверка:

```powershell
py -c "import selenium; print('ok')"
& "C:\Program Files\Java\jdk-25.0.2\bin\java.exe" -version
```

---

## 1. Вход в CurseForge (один раз)

Профиль Chrome лежит в `%LOCALAPPDATA%\AtlasMapExtended\cf_real` и **общий с
AtlasMapExtended**, поэтому если вы уже входили там — входить заново не нужно.
Если профиля нет, скрипт создаст `%LOCALAPPDATA%\BeezzCraftExtended\cf_real`.

```powershell
py tools\cf_real.py
```

Откроется Chrome. Войдите в аккаунт CurseForge (Google / Discord / GitHub /
Twitch, при необходимости 2FA). Скрипт напишет `SIGNED IN: ...`.

Проверить сессию в любой момент:

```powershell
py tools\cf_real.py --check
```

Свой путь к профилю можно задать переменной `CURSEFORGE_PROFILE`.

---

## 2. Сборка релиза

JAR'ы должны лежать в

```
C:\Users\<вы>\Documents\BeezzCraftExtended_all_builds
```

Имя файла — строго `BeezzCraft-<Loader>-<MC>-v<версия>.jar`, например:

```
BeezzCraft-Fabric-1.21-v1.1.jar
BeezzCraft-Fabric-1.21.4-v1.1.jar
BeezzCraft-NeoForge-26.3-v1.1.jar
```

По имени скрипт определяет загрузчик (`Fabric` / `NeoForge`), версию игры и
версию мода.

Собрать всё (17 веток × 2 загрузчика) можно тем же скриптом, которым вы
пользуетесь сейчас (`build_all_icon.py`), либо вручную в каждой ветке:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
.\gradlew.bat clean remapJar jarNeoForge    # для 1.21.x
.\gradlew.bat clean jar jarNeoForge         # для 26.x
```

---

## 3. Загрузка

Сначала «холостой» прогон — покажет, что и с какими метаданными уйдёт:

```powershell
py tools\cf_console.py --version v1.1 --dry-run
```

Только один файл (например, тест на 1.21 Fabric):

```powershell
py tools\cf_console.py --version v1.1 --only "Fabric-1.21-"
```

Всё:

```powershell
py tools\cf_console.py --version v1.1
```

Скрипт сам:
- поднимает Chrome на профиле автора, если он закрыт;
- берёт список допустимых версий проекта (`get-game-versions`);
- для каждого JAR отправляет `upload-file-details` + чанки по 5 МБ;
- повторяет запрос при `429 Too Many Requests` (5 попыток, 10–50 с);
- печатает результат по каждому файлу.

Метаданные: Environment = **Client**, Java = **21** для 1.21.x и **25** для 26.x,
тип релиза `release` (по умолчанию), changelog — общий текст v1.1.

Версии игры берутся из той же формы, что открывает кнопка **Add File** в
консоли (`create-project-file-form-data`). Поэтому скрипт работает и на новом
проекте, у которого `get-game-versions` ещё возвращает пустой список.

### Полезные ключи

| Ключ | Смысл |
|---|---|
| `--dry-run` | показать план, ничего не заливать |
| `--only "Fabric-1.21.4-"` | только файлы, в имени которых есть подстрока |
| `--names "a.jar,b.jar"` | только точные имена файлов |
| `--skip "NeoForge-"` | исключить подстроку (можно несколько раз) |
| `--limit N` | первые N файлов |
| `--sleep 8` | пауза между файлами (по умолчанию 1.5 с) |
| `--release-type beta` | тип релиза (`release` / `beta` / `alpha`) |
| `--changelog "..."` | свой changelog |
| `--changelog-file tools\changelog.md` | changelog из файла |
| `--list-versions` | показать id версий игры проекта |

---

## 4. Проверка результата

```powershell
py tools\cf_ui_files.py
```

Откроет страницу Files проекта и выведет её текст. Или списком через API:

```powershell
py tools\cf_files.py
```

Можно и глазами:
<https://authors.curseforge.com/#/projects/1817202/files>

Статусы: у уже одобренного проекта файл проходит `Processing` → `Approved`
(обычно минуты). У **нового проекта** файл получает статус **Pending Approval**
(в `cf_files.py` это `status 3`), пока модератор не одобрит проект; после
одобрения статус становится `4`. На публичной странице файл появляется после
этого.

---

## 5. Частые ситуации

### `429 Too Many Requests`
Скрипт уже ретраит. Если всё равно валится — `--sleep 8` и/или повторить
только упавшие через `--names`.

### Файл уже существует / «duplicate hash»
CurseForge не принимает файл с тем же содержимым. Варианты:
1. удалить старый файл в консоли (Files → строка → Delete), затем залить снова;
2. пересобрать, чтобы хэш изменился (`clean build` меняет таймстемпы в jar).

### `401 Unauthorized`
Сессия истекла. Запустите `py tools\cf_real.py`, войдите в открытом окне и
повторите загрузку.

### `game version '...' not offered`
Проект не разрешает эту версию игры/загрузчик/Java. Посмотрите
`--list-versions` и добавьте версию в настройках проекта на CurseForge.

---

## 6. Что остаётся ручным

- **Первый вход** в консоль (SSO + возможно 2FA).
- **Удаление файлов** — в UI консоли.
- **Модерация/одобрение** проекта.
