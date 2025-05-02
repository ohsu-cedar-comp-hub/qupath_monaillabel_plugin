# MiroSCOPE

**MiroSCOPE** is an AI-driven platform for **functional tissue unit (FTU) annotation**, built as an extension to [QuPath](https://qupath.github.io/), a popular open-source software for digital pathology image analysis.

## Overview

MiroSCOPE enhances QuPath by integrating advanced AI capabilities to support automated and semi-automated annotation of functional tissue units. This extension is designed to accelerate histological analysis and improve reproducibility in tissue-based studies.

## Key Features

- Seamless integration with QuPath
- AI-assisted FTU detection and annotation
- Support for large-scale histopathology image analysis

## Installation

To use MiroSCOPE, download the appropriate build for your system from the [dists](./dists) folder.

You may build from the source code in this repo by following the instruction in [QuPath_README](./QuPath_README.md).

## Getting Started with MiroSCOPE

To use MiroSCOPE, click the "MiroSCOPE" menu in the main menu bar. Choose the folder containing your images. 
The selected folder must contain two sub folders: images and annotations (optional). 
All your image files should be placed into the images folder. The annotations 
folder, if present, should contain annotation files for individual images. This folder structure **must be strictly followed**.

To test MiroSCOPE's features, we have provided a sample image set in the [test-images](./test-images) folder: 
- The [annotated_image](./test-images/annotated_image) folder as an example of a previously annotated image
- The [unannotated_image](./test-images/unannotated_image) folder as an example of an image that has not been annotated

## Source Code

The source code is available in the the [qupath-extension-cedar](./qupath-extension-cedar) folder.

## Inference and Fine-tuning

The Python-based backend used to support inference and model fine-tuning is hosted 
at [monailabel_cedar_app](https://github.com/ohsu-cedar-comp-hub/monailabel_cedar_app). 
Follow the instructions there to install the backend.

## Citation

If you use MiroSCOPE in your work, please cite the associated publication (TBD).

## License

As specified in this repo.

## Contact

For questions, feedback, or bug reports, please contact the development team or submit an issue through the appropriate repository.