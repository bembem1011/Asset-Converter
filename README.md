# Asset-Converter

## Introduction
**The Converter tools help Android developer convert resources:**
```
- svg to vector
- png to png/webp
- json to raw/json
- jpg to jpg
- h/xh/xxh/xxxhdpi to h/xh/xxh/xxxhdpi
- remove vector alpha
```

## Installation

- Script: `java -jar AssetConverter.jar --max-xml-size {max_vector_size} --input-folder {input_folder} `
- Example: `java -jar AssetConverter.jar --max-xml-size 10 --input-folder App_Assets`
```
Params:
- max_vector_size: the maximum svg file size will be converted to 
- input_folder: the folder that is converted
```
- Input folder includes: `File (svg, png, json). Folder (hdpi, xh, xxh, xxxhdpi folder)`
	
- Result: `asset/src/main/res`
