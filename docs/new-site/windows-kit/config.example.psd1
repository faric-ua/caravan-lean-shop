# Налаштування ЦЬОГО ноутбука (UTF-8 з BOM). Скопіюй у  %USERPROFILE%\.caravan-lean\config.psd1  і заповни.
# У репо цей файл НЕ кладемо (там шляхи й адреси хостингу). Паролів тут теж нема:
# вхід на хостинг - SSH-ключем (пункт 17), у GitHub - через gh (пункт 15).
@{
    # GitHub-репо нового сайту
    RepoSlug       = 'OWNER/caravan-lean-site'

    # Папка проєкту на Google Диску: папка ВЛАСНИКА, до якої він дав доступ виконавцю
    # (Google Drive для комп'ютера -> «Доступні мені» -> додати ярлик на «Мій диск»). Напр. 'G:\Мій диск\Caravan Lean'
    DrivePath      = 'G:\My Drive\Caravan Lean'

    # Адреси сайтів (для статусу й перевірок)
    ProdUrl        = 'https://shop.caravan-lean.com.ua/'
    StagingUrl     = ''          # напр. 'https://new.caravan-lean.com.ua/' - коли з'явиться

    # Хостинг Україна: панель adm.tools -> «SSH-доступ» -> «Дані для підключення»
    SshHost        = ''          # напр. 'xxxxxx.ftp.tools' - точно як у панелі
    SshPort        = 22
    SshUser        = ''

    # Сайти для резервної копії (пункт 10). Dir - папка сайту від домашньої теки на хостингу,
    # Db - назва бази (логін/пароль бази - у ~/.my.cnf НА ХОСТИНГУ, не тут). Порожній Dir - сайт пропускається.
    # Після запуску нового сайту - додати його рядком (Name = 'new'), старі не прибирати 3 місяці.
    Sites = @(
        @{ Name = 'shop'; Dir = ''; Db = '' }    # shop.caravan-lean.com.ua (магазин)
        @{ Name = 'main'; Dir = ''; Db = '' }    # caravan-lean.com.ua (сайт причепів)
    )

    # Локальний сайт (Docker)
    LocalUrl       = 'http://localhost:8080/'
}
