#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#

from faker import Faker
from babel.dates import format_date
import random
from datetime import datetime

fake = Faker()

# TOOD: If possible set date range on Faker

FORMATS = ['short',
           'medium',
           'long',
           'dd MMM YYY',
           'dd MMM',
           'dd, MMM YYY',
           'dd, MMM',
           'd MMM YYY',
           'd MMM',
           'd MMMM YYY',
           'MMMM YYY',
           'd MMMM, YYY',
           'd MMM, YYY',
           'd MM YY',
           'd MMMM YYY',
           'MMMM d YYY',
           'MMMM YYY',
           'MMM YYY',
           'MMMM d, YYY',
           'YYY',
           'dd.MM.YY',
           'dd.MM',
           'full',
           'full',
           'full',
           'full',
           'full',
           'full',
           'full',
           'full',
           'full',
           'full']

# TODO: maybe avoid duplicates, output dates also for other locales such as german, and french ...

with open('date_dev_deu.txt', 'w', encoding="utf-8") as f:
    for i in range(2000):
        dt = fake.date_time_ad(start_datetime=datetime(1900, 1, 1))

        format = random.choice(FORMATS)
        source_date = format_date(dt, format=format,  locale='en_US')
        target_date = format_date(dt, format='YYYYMMdd',  locale='en_US')

        if "short" not in format \
                and "medium" not in format \
                and "long" not in format \
                and "full" not in format :

            if "Y" not in format:
                target_date = "0000" + target_date[4:]

            if "d" not in format:
                target_date = target_date[:6] + "00"

            if "M" not in format:
                target_date = target_date[:4] + "00" + target_date[6:]

        f.write(target_date + '\t' + source_date + '\n')