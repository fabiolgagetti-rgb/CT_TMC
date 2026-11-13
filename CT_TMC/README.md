# SSDE Plugin for ImageJ/Fiji

This plugin calculates Size-Specific Dose Estimates (SSDE) from CT image sequences.

## Installation

1. Copy the `SSDE.jar` file from the `target` folder to your ImageJ/Fiji `plugins` folder.
2. Copy all `.jar` files from the `lib` folder to your ImageJ/Fiji `jars` folder.

> ⚠️ **Note**:
> - Some libraries may already exist — you can safely ignore duplicates.
> - If the version differs, delete the older one.
> - You can use the ImageJ uploader or manually delete older versions.

After copying, restart ImageJ/Fiji. The plugin will appear under **Plugins > SSDE**.

## How to Use

1. Click **"Select directory"** to choose the folder containing CT images (supports subfolders and sequences).
2. Open a sequence from the image tree to make it the active image.
3. Adjust **segmentation parameters** in the **Parameters** section.
4. Choose **output options** in the **Results** section.
5. Use **"Graph options"** to customize graph display.
6. Click **"Save results"** to export an Excel file with the analysis.

## Disclaimer

This software is provided *as is* without any warranties. The author is not liable for any outcomes derived from its usage.

## Contact

For questions, feedback or suggestions, please email: [fabio.gagetti@unifesp.br](mailto:fabio.gagetti@unifesp.br)
