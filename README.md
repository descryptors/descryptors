<img height="80px" src="/resources/public/img/logo.svg">

>  _The only prescription is more description._


__Descryptors__  makes finding and monitoring cryptocurrency projects easy. Our goal is to be transparent and neutral.

https://descryptors.io



## Building

To build __Descryptors__ you will need to install [__Clojure CLI tools__](https://clojure.org/guides/getting_started).


### Start Backend

```
clj -A:backend
```

After building the Clojure code, it will load the data files (if specified), start the webserver and a REPL on port `3311`.

See [conf/config.edn](/conf/config.edn).

Descryptors uses [__Roll__](https://github.com/dimovich/roll) for backend.

### Start Frontend

```
clj -A:frontend
```

This will start Figwheel which will build the ClojureScript code.

Descryptors uses [__Proto__](https://github.com/descryptors/proto) for React components and charts.

### Open

After building is done, navigate in your browser to `localhost:5000`. If you start only the backend, the pages will be rendered on the server.


## Data

The project comes with some sample data located in `data` folder. This includes basic information about the cryptocurrency projects and a precomputed index for marketcap and code activity.

Descryptors doesn't collect data by itself. For that you need to use [__Destreams__](https://github.com/descryptors/destreams).


## Overview

<img src="/resources/overview.png">


## License

Copyright © 2020 Descryptors team

Licensed under GNU General Public License version 3 (see [LICENSE](LICENSE)).
