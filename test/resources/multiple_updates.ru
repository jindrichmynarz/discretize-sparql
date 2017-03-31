PREFIX pc:        <http://purl.org/procurement/public-contracts#>
PREFIX schema:    <http://schema.org/>

WITH <http://linked.opendata.cz/resource/dataset/isvz.cz>
DELETE {
  ?resource schema:price ?value .
}
INSERT {
  ?resource schema:price ?interval .
}
WHERE {
  [] pc:agreedPrice ?resource .
  ?resource schema:priceCurrency "CZK" ;
    schema:price ?value .
};

CLEAR GRAPH <http://linked.opendata.cz/resource/dataset/isvz.cz>
