# User Interface Design Philosophy

## Overview

Wally Wallet presents as a fun, dynamic app, meant to be used all the time.  While money is serious, it recognises that a lot of blockchain applications 
are casual and entertaining uses of money!  Although Wally Wallet can be used for long term storage, it recognises its primary purpose is an often used
human "advocate/interpreter" into the world of blockchain.  As such its primary home is running on the user's main phone, and is meant to be pulled out, used, 
and closed just as often as you pull out cash or your credit card today.

It strives to provide the experienced user a powerful and efficient way to translate their intention into a blockchain action, repeatedly, without exhausting the
user or getting in the way of completion.

It provides a clean path for the beginning user to become experienced, and dynamically changes how it presents itself as the user becomes experienced.

## Concepts

**Fitt's Law**: The amount of time required for a person to move a pointer (e.g., mouse cursor) to a target area is a function of the distance to the target divided by the size of the target. https://ixdf.org/literature/topics/fitts-law

**Navigation Distance**: The time required for a user to move between specific application functions.  These application functions may be on the same page, or they require multiple navigation steps.  
This is an "overall" concept, encapsulating ideas like "Fitt's law", which pertains to the physical time and effort of selecting an onscreen target, the number of steps needed to get from A to B,
and the likelihood that the user gets "lost" on the way.

**User Interface graph**: The graph theory graph of every user interaction in the application, with each node being screen states and edges are direct transfers from one state to the next.

**Dark patterns**: Dark patterns are UI techniques, informed by psychological principles, that influence a user to pick a choice that is likely worse for the user.  

**Context**: The user's intention, the information needed to achieve that intention, and where the user is in the steps required to achieve that intention.

**Geographical reuse**: It is better if the same info appears in the same place with the same style on multiple screens.  This helps the user maintain context, and minimizes user education.

**Navigational reuse**: It is better if the same navigation tools work on multiple screens and are located in the same place.  This helps the user maintain context, and minimizes user education.

**User education**: A user needs to "get used to" your app.  This concept covers both abstract concepts, intuition, and reflexive actions.

**Affordances**: Characteristics of an UI that suggest how it can be used.  Affordances are important because the user interface feels more intuitive and has a shorter learning time.

## User Interface Structure

As a thought experiment if you examine an app's UI graph "zoomed out", certain aspects of its design philosophy become apparent.

Is the graph narrow and long?  Or is it wide and short?  How many cross connections are there?

Some modern UI design proposes that the overall design should be narrow and long.  You have heard these ideas in specifics if not in abstraction.
They correspond loosely to the idea that the designer should carry the user through a task using a multi-screen path in which a single concept is 
presented per screen, and the user should be presented with very limited navigation options (basically, BACK and NEXT buttons).  This design works for 
beginners and people who are not tech-savvy.

However problems are that it is easy for users to lose context, it is painful for regular users, and it allows/encourages dark patterns like making the 
navigation distance to app-undesired outcomes high, and using the Sunk Cost Fallacy to encourage users to do something they don't want to do.

Wally uses a wide and short design with many cross connections.  It strives to be as wide, short, and cross connected as possible while 
still being accessible to new users.  In our goal to create a wallet that is elegant and quick to use multiple times per day, by minimizing navigation distance
between commonly used items.  If we have to compromise on the new user experience we will do so, but we evolve the UI from a beginner to a more efficient one as
the user gains experience.

## Screen Complexity

A busier screen allows the application's UI graph to be wide, short, and highly cross connected, simply because there are more options per screen.  Wally's
screens are as busy as possible without making them look like they were designed in the 90s.

### Data fields

The user's entry of information has a huge "navigation distance" and so the value of that information is very high.

#### Persistence
Data fields should not lose their information even when dismissed.  They should only be cleared if the user's intention is completed or actively dismissed.  Upon
completion, data fields should not be cleared if the field's data would likely be useful in a subsequent operation.

#### Auto-Rejection

Do not auto-reject contents inputted into data fields, because this leads to user confusion ("Why didn't my paste work?").  And the user likely just needs to make a 
small fix-up to the information.

Do put a line below the input field describing what is wrong with the data.

#### Auto-correction

For unambiguous cases, it is valuable to autocorrect pasted, **but not typed**, data.  The best solution allows a double paste of the same data to override the autocorrect.

## Detailing: Bling and Gamification

UI "detailing" acts to increase comprehension and usability.  However once an app achieves a certain level , detailing becomes a sales tool -- it is meant to catch the eye and encourage use through rewards, excitement, and beauty.  There are 3 fundamental tools for that -- novelty, movement, and contrast.  
Movement and contrast are well known to activate primitive attention circuits in humans (for evolutionary survival reasons).  Novelty activates interest due to curiosity (also a primitive neural circuit).  However, it is possible to over-activate these circuits, resulting in user fatigue.  And if UI elements delay interaction too much, experienced users may become impatient and bored.

Sounds and animations should reward users for significant events like completing a transaction, receiving money, sending money, etc.

### Sound

All sounds must be able to be muted in settings, using the existing option.  It is not sufficient to use the OS sound capabilities for this because it is inconsistent across OSes, is painful to find,
and not controllable on a per-application basis on some OSes.

### Animations

Short, 1 second or less, gamification reward animations are encouraged.  These animations should not take over the full screen, to maintain context.  Instead the
animation plays on top of the screen while the app moves to the next state.  This allows the power user to mentally or physically set up their next step, reducing the
Fitt's Law distance to 0 (once the animation completes) for the next step.  Unobscured active screen elements must NOT be disabled during the animation play time.

## Confirmations

Confirmations increase the navigation distance, and may double it for simple tasks.  Confirmations torture people who use the app all the time.  Confirmations are therefore
discouraged and any confirmation should be skippable via an override in settings.  No reversible action may have a confirmation.  For example, if you quit the program, you can simply restart it.  
Never ask for a confirmation to remove data, because the user can type it in again.  However, data field contents should be persistent if the user navigates away and back, but never ask for a confirmation to remove data, so you should not have this problem!

Confirmations must not use popups.  Have the data entry screen transform to add a confirmation, reusing the space of the original "accept" button, reducing the Fitt's law
distance to zero.  Insert a tile with some text asking to confirm, and keep the existing information in-place on the screen.

Do not redundantly restate the information that the user has already entered.  This is a waste of development time and causes the user to lose context.
Ideally, transform the entry dialog boxes into text fields containing the same information, possibly supplementary info about that entry data near it.

**Operations that transfer assets away from the user should be confirmed (with quantity based overrides in settings).**

## Popups

Do not use popups.  Popups cause the user to lose context, because they interrupt the navigation flow and force the user to respond to them.  They also prevent navigation.
They obscure the information behind them, which is often relevant to the popup.  Use a tile.

## Dynamic screens

Dynamic screens are encouraged.  They help the user maintain context and maximize the information within the highly resource-limited mobile screens.

Screen changes should be animated in < 1/2 second rather than popping.






