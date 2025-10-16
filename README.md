# What it does right now, (not in order)
- recieves the channel update message
- filters the requests to only the counting channel (ignoring all others)
- checks if the message content is +1 or -1 of the last number
- also gets the last message in the channel, but might not work at first for bs, it uses cache.
- if the number is a correct number, nothing appears to happen, it does increase a “fame” value that I made though
- if number is incorrect, it deletes the message and increases a “shame” value I added.
- if the number is correct, it sets a variable “lastNumber” to that number, and it uses this every time it checks.
- has some commands,
— countstats displays fame and shame
— scorechanger changes a users fame/shame values (might disable this for the server, was useful when testing tho)
— other commands 
- all commands are only to the user (“ephemeral “)
