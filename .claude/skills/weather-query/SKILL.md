---
name: weather-query
description: 天气查询工具
disable-model-invocation: true
---
# 要求
使用中文返回结果

# 工具说明
Usage:

    $ curl wttr.in          # current location
    $ curl wttr.in/muc      # weather in the Munich airport

Supported location types:

    /paris                  # city name
    /~Eiffel+tower          # any location (+ for spaces)
    /Москва                 # Unicode name of any location in any language
    /muc                    # airport code (3 letters)
    /@stackoverflow.com     # domain name
    /94107                  # area codes
    /-78.46,106.79          # GPS coordinates

Moon phase information:

    /moon                   # Moon phase (add ,+US or ,+France for these cities)
    /moon@2016-10-25        # Moon phase for the date (@2016-10-25)

Units:

    m                       # metric (SI) (used by default everywhere except US)
    u                       # USCS (used by default in US)
    M                       # show wind speed in m/s

View options:

    0                       # only current weather
    1                       # current weather + today's forecast
    2                       # current weather + today's + tomorrow's forecast
    A                       # ignore User-Agent and force ANSI output format (terminal)
    d                       # restrict output to standard console font glyphs
    F                       # do not show the "Follow" line
    n                       # narrow version (only day and night)
    q                       # quiet version (no "Weather report" text)
    Q                       # superquiet version (no "Weather report", no city name)
    T                       # switch terminal sequences off (no colors)

PNG options:

    /paris.png              # generate a PNG file
    p                       # add frame around the output
    t                       # transparency 150
    transparency=...        # transparency from 0 to 255 (255 = not transparent)
    background=...          # background color in form RRGGBB, e.g. 00aaaa

Options can be combined:

    /Paris?0pq
    /Paris?0pq&lang=fr
    /Paris_0pq.png          # in PNG the file mode are specified after _
    /Rome_0pq_lang=it.png   # long options are separated with underscore

Localization:

    $ curl fr.wttr.in/Paris
    $ curl wttr.in/paris?lang=fr
    $ curl -H "Accept-Language: fr" wttr.in/paris

Supported languages:

    am ar af be bn ca da de el et fr fa gl hi hu ia id it lt mg nb nl oc pl pt-br ro ru ta tr th uk vi zh-cn zh-tw (supported)
    az bg bs cy cs eo es eu fi ga hi hr hy is ja jv ka kk ko ky lv mk ml mr nl fy nn pt pt-br sk sl sr sr-lat sv sw te uz zh zu he (in progress)

Special URLs:

    /:help                  # show this page
    /:bash.function         # show recommended bash function wttr()
    /:translation           # show the information about the translators

