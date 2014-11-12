
/*
 * GET home page.
 */
var mysql = require("mysql");
var connection =  mysql.createPool({
	host : 'localhost',
	user : 'root',
	password: 'root',
	port     : '3306',
	database : 'MetaDataStore',
	connectionLimit : '100'
});

exports.set = function(req, res){


	/*connection.connect();
	if (connection.connect()) {
		console.log("Connection Successful!!");
	}*/

	var strQuery = "insert into MetaData (uuid, nodeId) values (?,?)";
	var uuid = req.query.uuid;
	var nodeId = req.query.nodeId;

	console.log("uuid "+uuid);
	console.log("nodeId "+nodeId);
	if (uuid != null && nodeId != null) {
		connection.getConnection(function(err, connection){
			connection.query( strQuery, [uuid,nodeId],  function(err, rows){
				if(err)	{
					throw err;
				}else{
					console.log( rows );
				}
			});
			connection.release();
		});
	}
	res.statusCode = 200;
	res.end();
};


/*
 * GET home page.
 */

exports.retrive = function(req, res){

	/*connection.connect();
	if (connection.connect()) {
		console.log("Connection Successful!!");
	}*/

	var strQuery = "select * from MetaData where uuid = ?";
	var uuid = req.query.uuid;

	console.log("uuid "+uuid);

	if (uuid != null) {
		connection.getConnection(function(err, connection){
			connection.query( strQuery, [uuid],  function(err, rows){
				if(err)	{
					throw err;
				}else{
					console.log(JSON.stringify(rows) );
					res.send(JSON.stringify(rows));
					//res.render('index', { title: 'Express', uuid: rows.uuid, nodeId: rows.nodeId});
				}
			});

			connection.release();
		});
	}
	
};

exports.deleteUUID = function(req, res){

	var strQuery = "delete from MetaData where uuid = ?";
	var uuid = req.query.uuid;

	console.log("uuid "+uuid);

	if (uuid != null) {
		connection.getConnection(function(err, connection){
			connection.query( strQuery, [uuid],  function(err, rows){
				if(err)	{
					throw err;
				}else{
					console.log( rows );
				}
			});
			connection.release();
		});
	}
	res.statusCode = 200;
	res.end();
};