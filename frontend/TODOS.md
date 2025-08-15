# TODOS

## Required

- [x] create color themes
- [x] come up with api route schema to request and post the daily picks from our backend api
- [x] come up with route schema for our game
    - the index page should always load fast
    - we want one "main" entry route for each game
        - starting with the only one we have, the review-guesser game
- [x] the review guesser route should server side render and load the daily picks from our api route
  `{{host}}/api/review-game/today`
- [x] dummy show the appid and name for now
- [x] create a simple header that allows users to jump back to the homepage as well as a single button to get to the
  review-guesser page
- [x] implement the "game" flow
    - we've loaded the daily picks
    - we present one pick at a time to the user and present buttons for each bucket label
    - the user can then click one button to send his guess to the backend
    - the backend responds and we show the result in a modal / popover / something similar
    - that modal also has a 'next round' button, which sends the user to the next game pick - and so forth until the
      user has submitted a bucket choice for each game
    - we need to decide, how we want to handle these indiviual rounds. maybe one separate route for each?
        - /review-guesser/1, /review-guesser/2 etc?
        - these routes should also be server-side rendered and ideally use a cached result of the /today
- [x] use next/form and FormState to send each users 'guess' for that game/appid and bucket-guess to
  `{{host}}/api/review-game/guess`
- [x] display response (correct true/false and actual review count)
- [x] on guess bucket submit, lock the other buttons, to indicate, which one the user clicked
- [x] allow users to login via steam
    - use the given jwt / token / whatever to save the users picks to the backend database
    - if logged in, load a users pick / show, if the person has already picked a bucket for todays game.
- [x] add 'Share' Button, that adds a copy/paste message to the users clickboard - 'wordle' style, using emojis to
  represent the users success or failure. We should also track how 'far' away a users guess was from the result (1,2,3
  off, or exact hit - and award points based on this precision.)
- [x] add link to steam next to title `https://store.steampowered.com/app/<appId>`
- [x] after round submission, no button should be clickable again, not even the "chosen" one
- [x] show open in steam button after user has made the guess
- [x] add opengraph images
- [x] show genres as 'pills'
- [x] display the release date
- [ ] info section below, which displays the other "meta" info we've got in the SteamAppDetail object
- [x] display the price?
- [ ] display price in local format (comma vs dot, currency symbol positioning.) use locale from browser
- [x] auth todos:
    - [x] add logout to footer
    - [x] if logged in, hide steam login button
  - [x] if logged in, load a users guess for each round, instead of letting them guess again (which will be declined
      in the backend anyways)
  - [x] on user login, get the profile / account name as well, so that we can create a readable leaderboard
  - [x] if logged out, do not show logout button in footer
  - [x] if logged in, hide the 'reset today' button
- [x] add favicon

### Backend

- [x] do not pick games that are not released yet, or have been released in the last seven days.
- [ ] can we generate a blurhash on the backend, for each screenshot that we get?
    - https://github.com/hsch/blurhash-java/blob/master/src/main/java/io/trbl/blurhash/BlurHash.java
- [ ] do not add 429 error to excluded_app tables

## Do later

- [x] use custom fonts (github monaspace)
- [ ] hide link to 'review guesser' when on that page
- [x] add lightbox for screenshots
- [ ] use provided image as steam login button from /public

