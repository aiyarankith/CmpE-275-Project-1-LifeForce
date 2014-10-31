/*
 * copyright 2012, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.resources;

import java.io.FileInputStream;
import java.net.Authenticator.RequestorType;
import java.util.Properties;

import poke.server.managers.JobManager;
import poke.server.resources.Resource;
import poke.server.resources.ResourceUtil;
import poke.server.storage.jdbc.DatabaseStorage;
import eye.Comm.Payload;
import eye.Comm.PhotoHeader;
import eye.Comm.PhotoHeader.ResponseFlag;
import eye.Comm.PhotoPayload;
import eye.Comm.Ping;
import eye.Comm.PokeStatus;
import eye.Comm.Request;
import eye.Comm.Header.Routing;
import eye.Comm.NameSpace;
import eye.Comm.JobOperation;
import eye.Comm.NameSpaceOperation;
import eye.Comm.JobDesc;
import eye.Comm.JobOperation;
import eye.Comm.Request.Builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

public class JobResource implements Resource {
	protected DatabaseStorage storage = new DatabaseStorage() {
	};

	protected static Logger logger = LoggerFactory.getLogger("database");

	@Override
	public Request process(Request request) {

		logger.info("poke: " + request.getBody().getJobOp().getAction());

		/*String type = new String();
		type = request.getBody().getJobOp().getAction().toString();*/

		System.out.println("REQUEST::::::::::::::::::::::::"+request.toString());

		//Get the routing id
		Routing routing_id = request.getHeader().getRoutingId();
		//Get the operation read/write/delete from request header
		int requesttype = request.getHeader().getPhotoHeader().getRequestType().getNumber();


		Request.Builder rb = Request.newBuilder();
		Payload.Builder pb = Payload.newBuilder();
		PhotoPayload.Builder fb = PhotoPayload.newBuilder();
		Request reply = null;
		System.out.println("REQUEST TYPE::::::::::::   "+requesttype);
		//Reading Image from the database
		if(requesttype == PhotoHeader.RequestType.read.getNumber())
		{
			System.out.println("REQUEST outtttttttttttttttttttttt READ:::::::::::::: "+request.getHeader());
			System.out.println("REQUEST   "+request.getBody());
			ByteString value = storage.readImage(request.getBody().getPhotoPayload().getUuid());

			if(value != null){
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.success_VALUE, "Image Retived successfully"));
				rb.setBody(request.getBody());
			}
			else{
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.failure_VALUE, "Error Retriving image"));
			}
			fb.setData(value);
			pb.setPhotoPayload(fb.build());
			rb.setBody(pb.build());
			reply = rb.build();
		}

		// Writing Image to the Database
		else if(requesttype == PhotoHeader.RequestType.write.getNumber())
		{
			System.out.println("REQUEST outtttttttttttttttttttttt of WRITE ::::::::::::::::"+request.getBody().getPhotoPayload().getName());
			System.out.println("REQUEST   "+request.getBody().getPhotoPayload().getData());

			String status = storage.addImage(request.getBody().getPhotoPayload().getName(), request.getBody().getPhotoPayload().getData());
			if (status != null){
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.success_VALUE, "Image added successfully"));
				rb.setBody(request.getBody());
			}
			else {
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.failure_VALUE, "Error adding image"));
			}

			fb.setUuid(status);
			pb.setPhotoPayload(fb.build());
			rb.setBody(pb.build());
			reply = rb.build();
		}
		else if(requesttype == PhotoHeader.RequestType.delete.getNumber())
		{
			System.out.println("REQUEST outtttttttttttttttttttttt Delete:::::::::::::: "+request.getHeader());
			System.out.println("REQUEST   "+request.getBody());
			
			boolean value = storage.deleteImage(request.getBody().getPhotoPayload().getUuid());

			if(value == true){
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.success_VALUE, "Image Deleted successfully"));
				rb.setBody(request.getBody());
			}
			else{
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.failure_VALUE, "Error in Delete image"));
			}
	
			pb.setPhotoPayload(fb.build());
			rb.setBody(pb.build());
			reply = rb.build();


		}

		System.out.println("Reply::::::::::::::::::::::::"+reply.toString());

		return reply;
	}

}
