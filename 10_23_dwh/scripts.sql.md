## Скрипты DDL и ETL для хранилища ежедневных метрик криптовалют

За основу взят датасет: https://www.kaggle.com/philmohun/cryptocurrency-financial-data
Файл с сырыми данными ./raw-data/consolidated_coin_data.csv 

## STAGE 

### DDL

#### Метаданные 
```
CREATE TABLE IF NOT EXISTS CMA.ETL_FILE_LOAD (
	FILE_ID AUTO_INCREMENT(1, 1, 1) NOT NULL
	, SOURCE VARCHAR(128) NOT NULL
	, FILE_NAME VARCHAR(64) NOT NULL
	, BUSINESS_DT DATE NOT NULL
	, LOAD_TS TIMESTAMP NOT NULL
	, PRIMARY KEY (FILE_ID, SOURCE, FILE_NAME, BUSINESS_DT) ENABLED
)
ORDER BY SOURCE, FILE_NAME, BUSINESS_DT
UNSEGMENTED ALL NODES;
```

#### Таблица данных
```
CREATE TABLE IF NOT EXISTS CMA.STG_OPER (
	BUSINESS_DT DATE NOT NULL
	, DT DATE NOT NULL
	, CUR_ID VARCHAR(64) NOT NULL
	, PRICE_OPEN NUMERIC(18,6)
	, PRICE_MAX NUMERIC(18,6)
	, PRICE_MIN NUMERIC(18,6)
	, PRICE_CLOSE NUMERIC(18,6)
	, MARKET_VOL_USD NUMERIC(18,6) -- the monetary value of the currency traded in a 24 hour period, denoted in USD
	, MARKET_CAP_USD NUMERIC(18,6) -- market capitalization
	PRIMARY KEY (DT, CUR_ID)
)
ORDER BY
	DT,
	CUR_ID
SEGMENTED BY HASH(DT, CUR_ID) ALL NODES
;
```

### ETL : Загрузка данных в STAGE

#### Читаем данные из csv файла и загружаем в STAGE
```
CREATE FLEX TABLE csv_basic();
COPY csv_basic FROM '/mnt/share/consolidated_coin_data.csv' PARSER fcsvparser();

INSERT INTO CMA.STG_OPER (
	BUSINESS_DT,
	CUR_ID,
	DT,
	PRICE_OPEN,
	PRICE_MAX,
	PRICE_MIN,
	PRICE_CLOSE,
	MARKET_VOL_USD,
	MARKET_CAP_USD
)
SELECT
	CURRENT_DATE,
	"Currency::VARCHAR",
	"Date"::DATE,
	"Open"::NUMERIC,
	"High"::NUMERIC,
	"Low"::NUMERIC,
	"Close"::NUMERIC,
	REGEXP_REPLACE("Volume",'[^0-9]','')::!NUMERIC,
	REGEXP_REPLACE("Market Cap",'[^0-9]','')::!NUMERIC
FROM
	csv_basic
```

#### Загружаем метаданные
```
INSERT INTO CMA.ETL_FILE_LOAD (
	SOURCE
	, FILE_NAME
	, BUSINESS_DT
	, LOAD_TS
)
SELECT	DISTINCT
	'FILE_CSV' AS SOURCE
	, 'STG_OPER' AS FILE_NAME
	, stg.BUSINESS_DT
	, GETDATE() as LOAD_TS
FROM CMA.STG_OPER stg
-- filter out what has already been loaded
	LEFT JOIN CMA.ETL_FILE_LOAD fl
		ON fl.SOURCE = 'FILE_CSV'
			AND fl.FILE_NAME = 'STG_OPER'
			AND fl.BUSINESS_DT = stg.BUSINESS_DT
WHERE fl.FILE_ID IS NULL
;
```

## ODS
### DDL
#### Таблица данных 
```
CREATE TABLE IF NOT EXISTS CMA.ODS_OPER (
	FILE_ID INTEGER NOT NULL
	, LOAD_TS TIMESTAMP NOT NULL
	, BUSINESS_DT DATE NOT NULL
	, DT DATE NOT NULL
	, CUR_ID LONG VARCHAR(64) NOT NULL
	, PRICE_OPEN NUMERIC(18,6)
	, PRICE_MAX NUMERIC(18,6)
	, PRICE_MIN NUMERIC(18,6)
	, PRICE_CLOSE NUMERIC(18,6)
	, MARKET_VOL_USD NUMERIC(18,6) -- the monetary value of the currency traded in a 24 hour period, denoted in USD
	, MARKET_CAP_USD NUMERIC(18,6) -- market capitalization
	, PRIMARY KEY (FILE_ID, DT, CUR_ID) ENABLED
)
ORDER BY
	FILE_ID,
	DT,
	CUR_ID
SEGMENTED BY HASH(DT, CUR_ID) ALL NODES
PARTITION BY DT GROUP BY CALENDAR_HIERARCHY_DAY(DT, 1, 2)
;
```

### ETL 

#### Собираем данные и метаданные из STAGE во VIEW 
```
CREATE OR REPLACE VIEW CMA.V_STG_OPER_ODS_OPER AS
    SELECT
        fl.FILE_ID
        , fl.LOAD_TS
        , src.BUSINESS_DT
        , src.DT
        , src.CUR_ID
        , src.PRICE_OPEN
        , src.PRICE_MIN
        , src.PRICE_MAX
        , src.PRICE_CLOSE
        , src.MARKET_VOL_USD
        , src.MARKET_CAP_USD
    FROM CMA.STG_OPER src
        INNER JOIN (
            SELECT
                FILE_ID
                , LOAD_TS
                , SOURCE
                , FILE_NAME
                , BUSINESS_DT
            FROM CUR.ETL_FILE_LOAD
            LIMIT 1 OVER (PARTITION BY SOURCE, FILE_NAME, BUSINESS_DT ORDER BY LOAD_TS DESC)
            ) fl
                ON fl.SOURCE = 'FILE_CSV'
                    AND fl.FILE_NAME = 'STG_OPER'
                    AND fl.BUSINESS_DT = src.BUSINESS_DT
        LEFT JOIN CUR.ODS_OPER trg
            ON fl.FILE_ID = trg.FILE_ID
                AND src.BUSINESS_DT = trg.BUSINESS_DT
                AND src.DT = trg.DT
                AND src.CUR_ID = trg.CUR_ID
    WHERE trg.FILE_ID IS NULL
;
```
#### Загружаем все из VIEW в ODS 

```
INSERT INTO CMA.ODS_OPER (
	FILE_ID
	, LOAD_TS
	, BUSINESS_DT
	, DT
    , CUR_ID
    , PRICE_OPEN
    , PRICE_MIN
    , PRICE_MAX
    , PRICE_CLOSE
    , MARKET_VOL_USD
    , MARKET_CAP_USD
)
SELECT
	src.FILE_ID
	, src.LOAD_TS
	, src.BUSINESS_DT
	, src.DT
    , src.CUR_ID
    , src.PRICE_OPEN
    , src.PRICE_MIN
    , src.PRICE_MAX
    , src.PRICE_CLOSE
    , src.MARKET_VOL_USD
    , src.MARKET_CAP_USD
FROM CMA.V_STG_OPER_ODS_OPER src
;
```

## DDS

### DDL

#### HUB-таблица (сущность --- совокупность метрик отдельной валюты, полученных из файла-источника)
```
CREATE TABLE IF NOT EXISTS CMA.DDS_HUB_CUR (
	HK_CUR_ID VARCHAR(32) NOT NULL
    , FILE_ID INTEGER NOT NULL
    , LOAD_TS TIMESTAMP NOT NULL
    , CUR_ID LONG VARCHAR(64) NOT NULL
    , PRIMARY KEY (HK_CUR_ID) ENABLED
)
ORDER BY CUR_ID
SEGMENTED BY HASH(HK_CUR_ID) ALL NODES
;
```

#### SATLlITE-таблица (значения метрик для каждой совокупности)
```
CREATE TABLE IF NOT EXISTS CMA.DDS_ST_CUR_METRICS (
	HK_CUR_ID VARCHAR(32) NOT NULL
    , FILE_ID INTEGER NOT NULL
    , LOAD_TS TIMESTAMP NOT NULL
    , HASHDIFF VARCHAR(32) NOT NULL
    , DT DATE NOT NULL
	, PRICE_OPEN NUMERIC(18,6)
	, PRICE_MAX NUMERIC(18,6)
	, PRICE_MIN NUMERIC(18,6)
	, PRICE_CLOSE NUMERIC(18,6)
	, MARKET_VOL_USD NUMERIC(18,6)
	, MARKET_CAP_USD NUMERIC(18,6)
	, PRIMARY KEY (HK_CUR_ID, HASHDIFF) ENABLED
)
ORDER BY
	HK_CUR_ID
	,	LOAD_TS
SEGMENTED BY HASH(HK_CUR_ID) ALL NODES
PARTITION BY DT GROUP BY CALENDAR_HIERARCHY_DAY(DT, 1, 2)
;
```

### ETL

#### Загружаем данные для HUB-таблицы из STAGE во VIEW 

```
CREATE OR REPLACE VIEW CMA.V_STG_OPER_DDS_HUB_CUR AS
SELECT DISTINCT
	MD5(src.CUR_ID) AS HK_CUR_ID
    , fl.FILE_ID
    , fl.LOAD_TS
    , src.CUR_ID
FROM CMA.STG_OPER src
	INNER JOIN (
		SELECT
			FILE_ID
			, LOAD_TS
			, SOURCE
			, FILE_NAME
			BUSINESS_DT
		FROM CMA.ETL_FILE_LOAD
		LIMIT 1 OVER (PARTITION BY SOURCE, FILE_NAME, BUSINESS_DT ORDER BY LOAD_TS DESC)
		) fl
			ON fl.SOURCE = 'FILE_CSV'
				AND fl.FILE_NAME = 'STG_OPER'
				AND fl.BUSINESS_DT = src.BUSINESS_DT
	LEFT JOIN CMA.DDS_HUB_PRODUCT trg
		ON MD5(src.CUR_ID) = trg.HK_CUR_ID
WHERE trg.HK_CUR_ID IS NULL
;
```

#### Загружаем данные из view в HUB-таблицу 

```
INSERT INTO CMA.DDS_HUB_CUR (
	HK_CUR_ID
    , FILE_ID
    , LOAD_TS
    , CUR_ID
)
SELECT
	src.HK_CUR_ID
    , src.FILE_ID
    , src.LOAD_TS
    , src.CUR_ID
FROM CMA.V_STG_OPER_DDS_HUB_CUR src
;
```

#### Загружаем данные для SATELLITE-таблицы во view

```
CREATE OR REPLACE VIEW CMA.V_STG_OPER_DDS_ST_CUR_METRICS AS
SELECT
	MD5(src.CUR_ID) AS HK_CUR_ID
    , fl.FILE_ID
    , fl.LOAD_TS
    , MD5(
    	isnull(src.DT::VARCHAR,'NULL')
    	||isnull(src.PRICE_OPEN::VARCHAR,'NULL')
    	||isnull(src.PRICE_MIN::VARCHAR,'NULL')
    	||isnull(src.PRICE_MAX::VARCHAR,'NULL')
    	||isnull(src.PRICE_CLOSE::VARCHAR,'NULL')
    	||isnull(src.MARKET_VOL_USD::VARCHAR,'NULL')
    	||isnull(src.MARKET_CAP_USD::VARCHAR,'NULL')
    	) AS HASHDIFF
    , src.DT
    , src.PRICE_OPEN
    , src.PRICE_MIN
    , src.PRICE_MAX
    , src.PRICE_CLOSE
    , src.MARKET_VOL_USD
    , src.MARKET_CAP_USD
FROM CMA.STG_OPER src
	INNER JOIN (
		SELECT
			FILE_ID
            , LOAD_TS
            , SOURCE
            , FILE_NAME
            , BUSINESS_DT
		FROM CMA.ETL_FILE_LOAD
		LIMIT 1 OVER (PARTITION BY SOURCE, FILE_NAME, BUSINESS_DT ORDER BY LOAD_TS DESC)
		) fl
			ON fl.SOURCE = 'FILE_CSV'
				AND fl.FILE_NAME = 'STG_OPER'
				AND fl.BUSINESS_DT = src.BUSINESS_DT
	LEFT JOIN CMA.DDS_ST_CUR_METRICS trg
		ON MD5(src.CUR_ID) = trg.HK_CUR_ID
			AND MD5(
			    	isnull(src.DT::VARCHAR,'NULL')
			    	||isnull(src.PRICE_OPEN::VARCHAR,'NULL')
			    	||isnull(src.PRICE_MIN::VARCHAR,'NULL')
			    	||isnull(src.PRICE_MAX::VARCHAR,'NULL')
			    	||isnull(src.PRICE_CLOSE::VARCHAR,'NULL')
			    	||isnull(src.MARKET_VOL_USD::VARCHAR,'NULL')
			    	||isnull(src.MARKET_CAP_USD::VARCHAR,'NULL')
				) = trg.HASHDIFF
WHERE trg.HK_CUR_ID IS NULL
;
```
#### Загружаем данные из view в SATELLITE-таблицу

```
INSERT INTO CMA.DDS_ST_CUR_METRICS (
	HK_CUR_ID
    , FILE_ID
    , LOAD_TS
    , HASHDIFF
    , DT
    , PRICE_OPEN
    , PRICE_MIN
    , PRICE_MAX
    , PRICE_CLOSE
    , MARKET_VOL_USD
    , MARKET_CAP_USD
)
SELECT
	src.HK_CUR_ID
    , src.FILE_ID
    , src.LOAD_TS
    , src.HASHDIFF
    , src.DT
    , src.PRICE_OPEN
    , src.PRICE_MIN
    , src.PRICE_MAX
    , src.PRICE_CLOSE
    , src.MARKET_VOL_USD
    , src.MARKET_CAP_USD
FROM CMA.V_STG_OPER_DDS_ST_CUR_METRICS src
;
```

### DATA MART

#### Все данные 

```
CREATE OR REPLACE VIEW CMA.V_DDS_OPER AS
SELECT
	CUR_ID
    , DT
    , PRICE_OPEN
    , PRICE_MIN
    , PRICE_MAX
    , PRICE_CLOSE
    , MARKET_VOL_USD
    , MARKET_CAP_USD
FROM CMA.DDS_HUB_CUR hub
	INNER JOIN CMA.DDS_ST_CUR_METRICS st
		ON hub.HK_CUR_ID = st.HK_CUR_ID
LIMIT 1 OVER (PARTITION BY CUR_ID, DT ORDER BY st.LOAD_TS DESC)
;
```

#### Аггрегаты 

```
SELECT
	CUR_ID
    , DATE_TRUNC('MONTH', DT)::DATE AS MNTH
    , avg(PRICE_OPEN) AS AVG_PRICE_OPEN
    , avg(PRICE_MIN) AS AVG_PRICE_MIN
    , avg(PRICE_OPEN) AS AVG_PRICE_OPEN
    , avg(PRICE_CLOSE) AS AVG_PRICE_CLOSE
    , avg(MARKET_VOL_USD) AS AVG_MARKET_VOL_USD
    , avg(MARKET_CAP_USD) AS AVG_MARKET_CAP_USD   
FROM CMA.V_DDS_OPER
GROUP BY
	CUR_ID
    , DATE_TRUNC('MONTH', DT)::DATE
;
```