# Тестовий вивід XML файлів

## dataSources.xml
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<project version="4">
  <component name="DataSourceManagerImpl" format="xml" multifile-model="true">
    <data-source source="LOCAL" name="project-name" uuid="generated-uuid">
      <driver-ref>postgresql</driver-ref>
      <synchronize>true</synchronize>
      <jdbc-driver>org.postgresql.Driver</jdbc-driver>
      <jdbc-url>jdbc:postgresql://hostname:5432/projectdb</jdbc-url>
      <working-dir>$ProjectFileDir$</working-dir>
    </data-source>
  </component>
</project>
```

## dataSources.local.xml
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<project version="4">
  <component name="dataSourceStorageLocal">
    <data-source name="project-name" uuid="same-uuid-as-above">
      <database-info product="" version="" jdbc-version="" driver-name="" driver-version="" dbms="POSTGRES"/>
      <user-name>postgres</user-name>
      <schema-mapping>
        <introspection-scope>
          <node kind="database" qname="@">
            <node kind="schema" negative="1"/>
          </node>
        </introspection-scope>
      </schema-mapping>
      <ssh-properties>
        <enabled>true</enabled>
        <ssh-config-id>host-name</ssh-config-id>
      </ssh-properties>
    </data-source>
  </component>
</project>
```

## Основні зміни:

1. **Додано атрибут `standalone="no"`** до XML заголовка
2. **Покращено форматування** з правильними відступами
3. **Логіка видалення** тепер:
   - Видаляє порожні файли замість залишення їх з неправильною структурою
   - При помилці парсингу видаляє пошкоджений файл
   - Зберігає файл тільки якщо в ньому залишилися DataSource

4. **UUID синхронізація** між обома файлами
5. **Правильна структура** для `dataSources.local.xml` з усіма необхідними елементами

## Переваги нової реалізації:

- Файли не залишаються порожніми або пошкодженими
- Правильне XML форматування з `standalone="no"`
- Автоматичне очищення при видаленні всіх DataSource
- Краща обробка помилок 