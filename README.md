Agar.io Clone

A 3D game inspired by agar.io build with socket.io and three-js on top of node js

![Image](screenshot.png)

---

## How to Play

#### Game Basics
- Use WASD to move around 3D space
- Eat food and other players in order to grow your character (food respawns every time a player eats it).
- A player's **mass** is the sum of the mass of the food and other players eaten (plus their starting mass).
- **Objective**: Try to get as big as possible and eat other players.

#### Gameplay Rules
- Players who haven't eaten yet cannot be eaten as a sort of "grace" period. This invincibility fades once they gain mass.
- Everytime a player joins the game, **3** food particles will spawn.
- Everytime a food particle is eaten by a player, **1** new food particle will respawn.
- The more food you eat, the slower you move to make the game fairer for all.

---

## How to run

#### Requirements
To run / install this game, you'll need:
- NodeJS with NPM installed.
- socket.IO.
- Express.


#### Downloading the dependencies
After cloning the source code from Github, you need to run the following command to download all the dependencies (socket.IO, express, etc.):

```
npm install
```

#### Running the Server
After downloading all the dependencies, you can run the server with the following command:

```
npm start
```

The game will then be accessible at `http://localhost:3000` or the respective server installed on. The default port is `3000`, however this can be changed in config. Further elaboration is available on our [wiki](https://github.com/huytd/agar.io-clone/wiki/Setup).

---

To get reloading repl working, run:
lein repl
(ns user)
(reset)

and then after changing something
(reset)


To fire up figwheel run
lein figwheel