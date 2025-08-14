# TODOS

## Required

- [x] create color themes
- [x] come up with api route schema to request and post the daily picks from our backend api
- [ ] come up with route schema for our game
    - the index page should always load fast
    - we want one "main" entry route for each game
        - starting with the only one we have, the review-guesser game
- [x] the review guesser route should server side render and load the daily picks from our api route
  `{{host}}/api/review-game/today`
- [x] dummy show the appid and name for now
- [ ] use next/form and FormState to send each users 'guess' for that game/appid and bucket-guess to
  `{{host}}/api/review-game/guess`
- [ ] display response (correct true/false and actual review count)

## Do later

- [ ] use custom fonts (github monaspace)

