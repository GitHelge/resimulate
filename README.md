<h2 align="center"><a href="https://www.resimulate.de/en/"><img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" title="ReSimulate" alt="ReSimulateIcon"></a><br>ReSimulate</h2>
<p align="center"><strong>Rescue Simulator for cardiological training scenarios.</strong></p>


<!-- TABLE OF CONTENTS -->
## Table of Contents

- [Table of Contents](#table-of-contents)
- [About the Project](#about-the-project)
  - [Built With](#built-with)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
- [Usage](#usage)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [Licence](#licence)
- [Contact](#contact)
- [Acknowledgements](#acknowledgements)

## About the Project

![https://www.resimulate.de/en/images/tab12x.png](https://www.resimulate.de/en/images/tab12x.png)

Early 2019, the [ECG Emergency Simulator](https://github.com/SIMLabHAW/SIMLab-Emergency-Simulator) was developed and released as an open source web app to simulate the functions of an external defibrillator. The installation and usage however required fundamental knowledge of network technology which made the solution uncomfortable for most users. The idea of ReSimulate is therefore to incorporate the same (and more) features in this peer-to-peer Android App.

ReSimulate offers the possibility to easily connect two Android devices via Wifi/Bluetooth and start the training.

- Quick setup through the Google Play Store.
- Fast one-to-one connections for training.
- Intuitive User Interface.
- Many cardiological scenarios available.
- Solo mode for self-sufficient training.
- Scenario Designer to create your own scenarios.
- Share or download designed scenarios.

### Built With

The App was written in [Kotlin](https://kotlinlang.org/docs/reference/android-overview.html) with [Android Studio](https://developer.android.com/studio) (v3.5), whereever possible.
Some custom view classes had to be defined in Java...

## Getting Started

When cloning this repository, there is a **known pitfall** which stops the app from compiling. This is due to the [Firebase](https://firebase.google.com/) implementation used in this project. In the original implementation, the Firebase is used to store custom uploaded scenarios by the community. 

### Prerequisites

In order to download and customize Resimulate for your needs, there are tools you might need:
- [Git](https://git-scm.com/) (optionally with GUI -> e.g. [SourceTree](https://www.sourcetreeapp.com/))
- [Android Studio](https://developer.android.com/studio)

### Installation

There are multiple options who you can install ReSimulate: 
- The easiest way is by using the official version in the [Google PlayStore](https://play.google.com/store/apps/details?id=de.bauerapps.resimulate).
You can visit the [ReSimulate website](https://www.resimulate.de/en/) for more information about its features.

- Another Possibility is to **clone this repository** and compile a custom version of ReSimulate.

## Usage

To use the App, you need an Android smartphone or tablet with version > 19. Alternatively, you can use a simulator and try different functionalities. **Note**, that the simulator has no Bluetooth capability and is therefore not able to connect to other devices.

## Roadmap

At the moment, I have no plan to further extend the features of the App. It might be interesting to look into the possibility of scenario-guidelines (in textform) for the trainer, or for a 12 channel ECG.

## Contributing

If you want to contribute to the project, send me a pm. :-)

## Licence
Distributed under the MIT License. See LICENSE for more information.

## Contact

Feel free to create an issue or concact me via pm @GitHelge.

## Acknowledgements

In this project, some libraries are used, which I like to mention here:

- UI elements are mostly taken from the [Android-Bootstrap](https://github.com/Bearded-Hen/Android-Bootstrap) project.
- Icons come from [Font-Awesome v4.7.0](https://fontawesome.com/v4.7.0/).
- Alerts were designed by F0ris as [Sweet-Alert-Dialogs](https://github.com/F0RIS/sweet-alert-dialog).
- ECG Simulation was inspired by the script from Karthik Raviprakash, available at[ecg-simulation-using-matlab](https://www.mathworks.com/matlabcentral/fileexchange/10858-ecg-simulation-using-matlab)
- Sound generation was inspired by the [Zentone Library](https://github.com/nisrulz/zentone) from [Nishant Srivastava](https://github.com/nisrulz)
