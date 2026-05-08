1. both players open the jar file
2. join or create socket link, create -> host, join->other player
3. both players join
4. host gets to choose game settings
5. host starts game (game features insignificant and can be modified)
6. game ends and both players are directed to score page
7. host screen has play again option and play again(stay in socket) with differnt settings(stay in socket) or exit (exit socket)
8. other player has exit(exit socket) or play again (stay in socket)
9. if either one exits, both players are redirected to join/create page
10. if both play again, jump to step 5
11. edge case: if either player exists mid game, player in game wins

using: JAVAFX, jdk 25, Java-WebSocket

if (k > 22) player auto wins